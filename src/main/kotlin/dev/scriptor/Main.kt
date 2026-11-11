package dev.scriptor

import dev.scriptor.conf.*
import dev.scriptor.elf.*
import dev.scriptor.gdb.GDBServer
import dev.scriptor.impl.TrapException
import dev.scriptor.impl.device.*
import dev.scriptor.isa.Registry
import dev.scriptor.machine.Device
import dev.scriptor.machine.Machine
import dev.scriptor.machine.MachineConfig
import dev.scriptor.util.*
import dev.scriptor.util.Log.format
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

// https://riscv.github.io/riscv-isa-manual/snapshot/privileged/
// https://riscv.github.io/riscv-isa-manual/snapshot/unprivileged/
// https://en.wikipedia.org/wiki/Executable_and_Linkable_Format
// https://wiki.osdev.org/RISC-V_Bare_Bones

fun main(args: Array<String>) {

    val logThread = Thread(Log::handle, "rvm-log")
    logThread.start()

    try {
        val context = ArgContext()
        context.parse(args)

        if ("--level" in context) {
            context["--level", { Log.level = it.toUInt() }]
        }

        val machine = init(context)

        context[
            "--load",
            Consumer { entry ->
                val parts = entry
                    .split("=".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val filename = parts[0]
                val offset = if (parts.size == 2) parts[1].toULong(0x10) else 0UL

                val entryPoint = load(machine, filename, offset)

                machine.entryPoint = entryPoint
            },
        ]

        run(context, machine)
    } catch (e: Exception) {
        Log.error("%s", e)
    }

    Log.shutdown()
    logThread.join()
}

fun init(args: ArgContext): Machine {
    val registry = Registry()

    Resource.names
        .filter { it.endsWith(".isa") }
        .forEach { Resource.read(true, it, registry::parse) }

    val isResource = "--config" !in args || "--config-file" !in args
    val filename = if ("--config" in args) args["--config"] else "config/default.conf"

    return Resource.read<Machine>(isResource, filename) { stream ->
        val machineConfig = MachineConfig()
        val parser = Parser(stream)
        val root = parser.parse()

        if ("mode" in root) {
            val mode = root[IntegerNode::class, "mode"].value
            machineConfig.mode(mode.toUInt())
        }

        if ("harts" in root) {
            val harts = root[IntegerNode::class, "harts"].value
            machineConfig.harts(harts.toUInt())
        }

        if ("order" in root) {
            val order = root[StringNode::class, "order"].value
            machineConfig.order(
                when (order) {
                    "le" -> ByteOrder.LITTLE_ENDIAN
                    "be" -> ByteOrder.BIG_ENDIAN
                    else -> throw NoSuchElementException(order)
                },
            )
        }

        if ("devices" in root) {
            val devices = root[ArrayNode::class, "devices"]
            for (node in devices) {
                val generator: Function<Machine, Device> = when (val type = node[StringNode::class, "type"].value) {
                    "clint" -> {
                        val begin = node[IntegerNode::class, "begin"].value
                        Function { CLINT(it, begin) }
                    }

                    "plic" -> {
                        val begin = node[IntegerNode::class, "begin"].value
                        val ndev = node[IntegerNode::class, "ndev"].value
                        Function { PLIC(it, begin, ndev.toUInt()) }
                    }

                    "uart" -> {
                        val begin = node[IntegerNode::class, "begin"].value

                        val inputNode = node[ObjectNode::class, "in"]
                        val inputType = inputNode[StringNode::class, "type"].value

                        val closeInput: Boolean
                        val inputStream = when (inputType) {
                            "system" -> {
                                closeInput = false

                                System.`in`
                            }

                            "file" -> {
                                closeInput = true

                                val name = inputNode[StringNode::class, "name"].value
                                FileInputStream(name)
                            }

                            else -> throw NoSuchElementException(format("type=%s", inputType))
                        }

                        val outputNode = node[ObjectNode::class, "out"]
                        val outputType = outputNode[StringNode::class, "type"].value

                        val closeOutput: Boolean
                        val outputStream = when (outputType) {
                            "system" -> {
                                closeOutput = false

                                System.out
                            }

                            "file" -> {
                                closeOutput = true

                                val name = outputNode[StringNode::class, "name"].value
                                val append =
                                    ("append" in outputNode) && outputNode[BooleanNode::class, "append"].value

                                FileOutputStream(name, append)
                            }

                            else -> throw NoSuchElementException(format("type=%s", outputType))
                        }

                        Function { UART(it, begin, inputStream, closeInput, outputStream, closeOutput) }
                    }

                    "memory" -> {
                        val begin = node[IntegerNode::class, "begin"].value
                        val capacity = node[IntegerNode::class, "capacity"].value
                        val readonly = ("readonly" in node) && node[BooleanNode::class, "readonly"].value

                        Function { Memory(it, begin, capacity.toUInt(), readonly) }
                    }

                    "tree" -> {
                        val begin = node[IntegerNode::class, "begin"].value

                        Function { DeviceTree(it, begin) }
                    }

                    "gpio" -> {
                        val begin = node[IntegerNode::class, "begin"].value

                        Function { GPIO(it, begin) }
                    }

                    "efuse" -> {
                        val begin = node[IntegerNode::class, "begin"].value

                        Function { EFuse(it, begin) }
                    }

                    "rng" -> {
                        val begin = node[IntegerNode::class, "begin"].value

                        Function { RNG(it, begin) }
                    }

                    "timer_group" -> {
                        val begin = node[IntegerNode::class, "begin"].value
                        val timers = if ("timers" in node) node[IntegerNode::class, "timers"].value else 0UL
                        val watchdogs = if ("watchdogs" in node) node[IntegerNode::class, "watchdogs"].value else 0UL

                        Function { TimerGroup(it, begin, timers.toInt(), watchdogs.toInt()) }
                    }

                    else -> throw NoSuchElementException(format("type=%s", type))
                }

                machineConfig.device(generator)
            }
        }
        machineConfig.configure(registry)
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
                        gdb.stop(e.id, 0x05U)
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
        while (machine.active) {
            machine.step()
        }
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
): ULong {
    val stream = ChannelInputStream(filename)

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

        for (ph in phtab) {
            if (ph.type != 0x01U) continue

            stream.seek(ph.offset.toLong())

            val data = ByteArray(ph.fileSize.toInt())
            val read = stream.read(data)
            if (read != data.size) {
                Log.warn("stream read %d, requested %d", read, data.size)
            }

            val address = ph.pAddress + offset
            val capacity = ph.memSize.toUInt()

            val memory = machine[Memory::class, address, capacity] ?: throw NoSuchElementException(
                format(
                    "no memory at address %016x with minimum capacity %d",
                    address,
                    capacity,
                ),
            )

            memory.direct(data, (address - memory.begin).toUInt(), true)
        }

        return header.entry + offset
    }

    stream.seek(0L)

    val capacity = stream.size().toUInt()
    val memory = machine[Memory::class, offset, capacity] ?: throw NoSuchElementException(
        format(
            "no memory at address %016x with minimum capacity %d",
            offset,
            capacity,
        ),
    )

    val data = ByteArray(stream.size().toInt())
    val read = stream.read(data)
    if (read != data.size) {
        Log.warn("stream read %d, requested %d", read, data.size)
    }

    memory.direct(data, (offset - memory.begin).toUInt(), true)

    return offset
}
