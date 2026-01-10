package io.scriptor

import io.scriptor.conf.*
import io.scriptor.elf.*
import io.scriptor.gdb.GDBServer
import io.scriptor.impl.TrapException
import io.scriptor.impl.device.CLINT
import io.scriptor.impl.device.Memory
import io.scriptor.impl.device.PLIC
import io.scriptor.impl.device.UART
import io.scriptor.isa.Registry
import io.scriptor.machine.Device
import io.scriptor.machine.Machine
import io.scriptor.machine.MachineConfig
import io.scriptor.util.*
import io.scriptor.util.Log.format
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.function.Function

// https://riscv.github.io/riscv-isa-manual/snapshot/privileged/
// https://riscv.github.io/riscv-isa-manual/snapshot/unprivileged/
// https://en.wikipedia.org/wiki/Executable_and_Linkable_Format
// https://wiki.osdev.org/RISC-V_Bare_Bones

fun main(args: Array<String>) {

    val logThread = Thread({ Log.handle() }, "rvm-log")
    logThread.start()

    try {
        val context = ArgContext()
        context.parse(args)

        val machine = init(context)
        run(context, machine)
    } catch (e: Exception) {
        Log.error("%s", e)
    }

    Log.shutdown()
    logThread.join()
}

fun init(args: ArgContext): Machine {
    val registry = Registry.instance
    Resource.read(true, "index.list") { stream ->
        stream
            .bufferedReader()
            .lines()
            .map { line -> line.trim { it <= ' ' } }
            .filter { line -> !line.isEmpty() }
            .filter { line -> line.endsWith(".isa") }
            .forEach { name -> Resource.read(true, name) { registry.parse(it) } }
    }

    val isResource = "--config" !in args
    val resourceName = if (isResource) "config/default.conf" else args["--config"]

    return Resource.read<Machine>(isResource, resourceName) { stream ->
        val machineConfig = MachineConfig()
        val parser = Parser(stream)
        val root = parser.parse()

        if ("mode" in root) {
            val mode = root[IntegerNode::class.java, "mode"].value
            machineConfig.mode(mode.toUInt())
        }

        if ("harts" in root) {
            val harts = root[IntegerNode::class.java, "harts"].value
            machineConfig.harts(harts.toUInt())
        }

        if ("order" in root) {
            val order = root[StringNode::class.java, "order"].value
            machineConfig.order(
                when (order) {
                    "le" -> ByteOrder.LITTLE_ENDIAN
                    "be" -> ByteOrder.BIG_ENDIAN
                    else -> throw NoSuchElementException(order)
                },
            )
        }

        args[
            "--load",
            { entry ->
                val parts = entry
                    .split("=".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val filename: String = parts[0]
                val offset = if (parts.size == 2) parts[1].toULong(0x10) else 0UL
                machineConfig.device { load(it, filename, offset) }
            },
        ]

        if ("devices" in root) {
            val devices = root[ArrayNode::class.java, "devices"]
            for (node in devices) {
                val type = node[StringNode::class.java, "type"].value

                val generator: Function<Machine, Device> = when (type) {
                    "clint" -> {
                        val begin = node[IntegerNode::class.java, "begin"].value
                        Function { CLINT(it, begin) }
                    }

                    "plic" -> {
                        val begin = node[IntegerNode::class.java, "begin"].value
                        val ndev = node[IntegerNode::class.java, "ndev"].value
                        Function { PLIC(it, begin, ndev.toUInt()) }
                    }

                    "uart" -> {
                        val begin = node[IntegerNode::class.java, "begin"].value

                        val inputNode = node[ObjectNode::class.java, "in"]
                        val inputType = inputNode[StringNode::class.java, "type"].value

                        val closeInput: Boolean
                        val inputStream = when (inputType) {
                            "system" -> {
                                closeInput = false

                                System.`in`
                            }

                            "file" -> {
                                closeInput = true

                                val name = inputNode[StringNode::class.java, "name"].value
                                FileInputStream(name)
                            }

                            else -> throw NoSuchElementException(format("type=%s", inputType))
                        }

                        val outputNode = node[ObjectNode::class.java, "out"]
                        val outputType = outputNode[StringNode::class.java, "type"].value

                        val closeOutput: Boolean
                        val outputStream = when (outputType) {
                            "system" -> {
                                closeOutput = false

                                System.out
                            }

                            "file" -> {
                                closeOutput = true

                                val name = outputNode[StringNode::class.java, "name"].value
                                val append =
                                    ("append" in outputNode) && outputNode[BooleanNode::class.java, "append"].value

                                FileOutputStream(name, append)
                            }

                            else -> throw NoSuchElementException(format("type=%s", outputType))
                        }

                        Function { UART(it, begin, inputStream, closeInput, outputStream, closeOutput) }
                    }

                    "memory" -> {
                        val begin = node[IntegerNode::class.java, "begin"].value
                        val capacity = node[IntegerNode::class.java, "capacity"].value
                        val readonly = ("readonly" in node) && node[BooleanNode::class.java, "readonly"].value

                        Function { Memory(it, begin, capacity.toUInt(), readonly) }
                    }

                    else -> throw NoSuchElementException(format("type=%s", type))
                }

                machineConfig.device(generator)
            }
        }
        machineConfig.configure()
    }
}

fun run(args: ArgContext, machine: Machine) {
    machine.reset()

    val debug = "--debug" in args
    val port = args["--port", { it.toUInt() }, { 1234U }]

    if (debug) {
        try {
            GDBServer(machine, port).use { gdb ->
                while (gdb.step()) try {
                    machine.step()
                } catch (e: Exception) {
                    machine.pause()
                    Log.inject { out -> machine.dump(out) }
                    Log.error("machine exception: %s", e)

                    if (e is TrapException) {
                        gdb.stop(e.id, 0x05u)
                    }
                }
            }
        } catch (e: IOException) {
            Log.error("failed to create gdb stub: %s", e)
        }
        return
    }

    try {
        machine.spin()
        while (true) machine.step()
    } catch (e: Exception) {
        machine.pause()
        Log.inject { out -> machine.dump(out) }
        Log.error("machine exception: %s", e)
    }

    try {
        machine.close()
    } catch (e: Exception) {
        Log.error("closing machine: %s", e)
    }
}

fun load(
    machine: Machine,
    filename: String,
    offset: ULong,
): Device {
    try {
        ChannelInputStream(filename).use { stream ->
            val buffer = ByteBuffer.allocateDirect(0x10)
            stream.read(buffer)
            val identity = Identity.read(buffer.flip())

            val elf = Arrays.compare(identity.magic, byteArrayOf(0x7F, 0x45, 0x4C, 0x46)) == 0
            if (elf) {
                val header = Header.read(identity, stream)

                val phtab = Array(header.phnum.toInt()) {
                    stream.seek((header.phoff + it.toUInt() * header.phentsize).toLong())
                    ProgramHeader.read(identity, stream)
                }
                val shtab = Array(header.shnum.toInt()) {
                    stream.seek((header.shoff + it.toUInt() * header.shentsize).toLong())
                    SectionHeader.read(identity, stream)
                }

                val shstrtab: SectionHeader = shtab[header.shstrndx.toInt()]

                var symtab: SectionHeader? = null
                var dynsym: SectionHeader? = null
                var strtab: SectionHeader? = null
                var dynstr: SectionHeader? = null
                for (sh in shtab) {
                    stream.seek((shstrtab.offset + sh.name).toLong())
                    val name = ByteUtil.readString(stream)
                    when (name) {
                        ".symtab" -> symtab = sh
                        ".dynsym" -> dynsym = sh
                        ".strtab" -> strtab = sh
                        ".dynstr" -> dynstr = sh
                    }
                }

                if (symtab != null && strtab != null) {
                    ELF.readSymbols(identity, stream, symtab, strtab, machine.symbols, offset)
                }
                if (dynsym != null && dynstr != null) {
                    ELF.readSymbols(identity, stream, dynsym, dynstr, machine.symbols, offset)
                }

                var begin = 0UL.inv()
                var end = 0UL

                for (ph in phtab) {
                    if (ph.type != 0x01U) continue

                    if (begin > ph.paddr) begin = ph.paddr
                    if (end < ph.paddr + ph.memsz) end = ph.paddr + ph.memsz
                }

                val rom = Memory(machine, begin + offset, (end - begin).toUInt(), false)

                for (ph in phtab) {
                    if (ph.type != 0x01U) continue

                    stream.seek(ph.offset.toLong())

                    val data = ByteArray(ph.filesz.toInt())
                    val read: Int = stream.read(data)
                    if (read != data.size) Log.warn("stream read %d, requested %d", read, data.size)

                    rom.direct(data, (ph.paddr - begin).toUInt(), true)
                }

                return rom
            } else {
                stream.seek(0L)

                val rom = Memory(machine, offset, stream.size().toUInt(), false)

                val data = ByteArray(stream.size().toInt())
                val read: Int = stream.read(data)
                if (read != data.size) Log.warn("stream read %d, requested %d", read, data.size)

                rom.direct(data, 0U, true)
                return rom
            }
        }
    } catch (e: IOException) {
        Log.error("failed to load file '%s' (offset %x): %s", filename, offset, e)
        throw RuntimeException(e)
    }
}
