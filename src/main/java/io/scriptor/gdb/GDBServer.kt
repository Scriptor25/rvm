package io.scriptor.gdb

import io.scriptor.isa.CSR
import io.scriptor.machine.Machine
import io.scriptor.util.Log
import io.scriptor.util.Log.format
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.function.Consumer

class GDBServer(private val machine: Machine, port: UInt) : Closeable {

    private val channel: ServerSocketChannel
    private val selector: Selector

    private var client: SocketChannel? = null

    private var gId = 0

    private val breakpoints: MutableMap<ULong, UInt> = HashMap()

    init {
        machine.setBreakpointHandler { id ->
            machine.pause()
            stop(client!!, id, 0x05u)
        }

        channel = ServerSocketChannel.open()
        channel.configureBlocking(false)
        channel.bind(InetSocketAddress(port.toInt()))

        selector = Selector.open()
        channel.register(selector, SelectionKey.OP_ACCEPT)

        Log.info("GDB listening on port %d", port)
        Log.info("local: %s", channel.localAddress)
    }

    fun step(): Boolean {
        try {
            if (selector.selectNow() <= 0) {
                return true
            }

            val it = selector.selectedKeys().iterator()
            while (it.hasNext()) {
                val key = it.next()
                it.remove()

                if (key.isAcceptable) {
                    handleAccept(key)
                    continue
                }

                handleRead(key)
            }

            return true
        } catch (e: InterruptedException) {
            Log.error("gdb interrupted: %s", e)
            return false
        } catch (e: Exception) {
            Log.error("gdb error: %s", e)
            return false
        }
    }

    fun stop(channel: SocketChannel, id: Int, code: UInt) {
        val state = channel.keyFor(selector).attachment() as ClientState
        state.stopId = id
        state.stopCode = code

        val payload: String = if (id < 0) format("S%02x", code) else format("T%02xcore:%x;", code, id)
        try {
            writePacket(channel, payload)
        } catch (e: IOException) {
            Log.error("gdb: %s", e)
        }
    }

    fun stop(id: Int, code: UInt) {
        stop(client!!, id, code)
    }

    override fun close() {
        channel.close()
    }

    private fun handleAccept(key: SelectionKey) {
        client = channel.accept()
        client!!.configureBlocking(false)
        client!!.register(selector, SelectionKey.OP_READ, ClientState())
        Log.info("gdb: accepted connection from %s", client!!.remoteAddress)
    }

    private fun handleRead(key: SelectionKey) {
        val client = key.channel() as SocketChannel
        val state = key.attachment() as ClientState

        val read = client.read(state.buffer)
        if (read < 0) {
            client.close()
            return
        }

        state.buffer.flip()
        while (state.buffer.hasRemaining()) {
            val b = state.buffer.get().toInt() and 0xFF
            process(b, client, state)
        }
        state.buffer.compact()
    }

    private fun process(b: Int, client: SocketChannel, state: ClientState) {
        if (state.append) {
            if (b == '#'.code) {
                state.checksum = state.checksum and 0xFFu
                state.append = false
                state.end = true
                state.packetChecksum.setLength(0)
                return
            }

            state.packet.appendCodePoint(b)
            state.checksum += b.toUInt()
            return
        }

        if (state.end) {
            state.packetChecksum.appendCodePoint(b)
            if (state.packetChecksum.length != 2) {
                return
            }

            state.end = false

            val checksum = state.packetChecksum.toString().toUInt(0x10)
            if (checksum != state.checksum) throw IOException(
                format(
                    "checksum mismatch: %02x != %02x",
                    checksum,
                    state.checksum,
                ),
            )

            if (!state.noack) {
                writeRaw(client, "+")
            }

            val request = state.packet.toString()
            Log.info("--> %s", request)
            val response = handle(request, state)
            Log.info("<-- %s", response)
            writePacket(client, response)
            return
        }

        if (b == '+'.code) {
            // acknowledge
            return
        }

        if (b == '-'.code) {
            // resend
            return
        }

        if (b == 0x03) {
            // interrupt
            machine.pause()
            stop(client, -1, 0x02U)
            return
        }

        if (b == '$'.code) {
            // payload begin
            state.append = true
            state.checksum = 0x00U
            state.packet.setLength(0)
            return
        }

        Log.info("unhandled request byte: %02x (%c)", b, if (b <= 0x20) ' ' else b.toChar())
    }

    private fun writeRaw(client: SocketChannel, data: String) {
        val bytes = data.toByteArray()
        val buffer = ByteBuffer.wrap(bytes)
        client.write(buffer)
    }

    private fun writePacket(client: SocketChannel, payload: String) {
        var checksum = 0
        for (b in payload.toByteArray()) {
            checksum = (checksum + b) and 0xFF
        }

        val packet: String = format("$%s#%02x", payload, checksum)
        writeRaw(client, packet)
    }

    private fun handle(payload: String, state: ClientState): String {
        when (payload[0]) {
            '?' -> return if (state.stopId < 0)
                format("S%02x", state.stopCode)
            else
                format("T%02xcore:%x;", state.stopCode, state.stopId)

            'c' -> {
                machine.spin()
                return "OK"
            }

            's' -> {
                machine.spinOnce()
                return "OK"
            }

            'r', 'R' -> {
                machine.reset()
                return "OK"
            }

            'D' -> return "OK"
            'g' -> {
                val hart = machine.harts[gId]
                val gprFile = hart.gprFile
                val fprFile = hart.fprFile
                val csrFile = hart.csrFile
                val pc = hart.pc

                val response = StringBuilder()

                // GPR
                for (i in 0U..31U) {
                    response.append(toHexString(gprFile.getdu(i), 8U))
                }

                response.append(toHexString(pc, 8U))

                // FPR
                for (i in 0U..31U) {
                    response.append(toHexString(fprFile.getdr(i), 8U))
                }

                response.append(toHexString(csrFile.getdu(CSR.fcsr, CSR.CSR_M), 4U))

                // CSR
                response.append(toHexString(csrFile.getdu(CSR.mstatus, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.misa, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.mie, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.mtvec, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.mscratch, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.mepc, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.mcause, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.mtval, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.mip, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.cycle, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.time, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.instret, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.sstatus, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.sie, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.stvec, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.sscratch, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.sepc, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.scause, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.stval, CSR.CSR_M), 8U))
                response.append(toHexString(csrFile.getdu(CSR.sip, CSR.CSR_M), 8U))

                return response.toString()
            }

            'G' -> {
                val hart = machine.harts[gId]
                val gprFile = hart.gprFile
                val fprFile = hart.fprFile
                val csrFile = hart.csrFile

                var p = 1

                // GPR
                for (i in 0U..31U) {
                    p = extractd(payload, p) { gprFile[i] = it }
                }

                p = extractd(payload, p) { hart.pc = it }

                // FPR
                for (i in 0U..31U) {
                    p = extractd(payload, p) { fprFile[i] = it }
                }

                p = extractw(payload, p) { csrFile[CSR.fcsr, CSR.CSR_M] = it.toULong() }

                // CSR
                p = extractd(payload, p) { csrFile[CSR.mstatus, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.misa, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.mie, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.mtvec, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.mscratch, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.mepc, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.mcause, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.mtval, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.mip, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.cycle, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.time, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.instret, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.sstatus, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.sie, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.stvec, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.sscratch, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.sepc, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.scause, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.stval, CSR.CSR_M] = it }
                p = extractd(payload, p) { csrFile[CSR.sip, CSR.CSR_M] = it }

                return "OK"
            }

            'p' -> {
                val hart = machine.harts[gId]
                val gprFile = hart.gprFile
                val fprFile = hart.fprFile
                val csrFile = hart.csrFile
                val pc = hart.pc

                val n = payload.substring(1).toUInt(0x10)

                // GPR
                if (n in 0U..31U) {
                    toHexString(gprFile.getdu(n), 8U)
                }

                if (n == 32U) {
                    toHexString(pc, 8U)
                }

                // FPR
                if (n in 33U..64U) {
                    toHexString(fprFile.getdr(n - 33U), 8U)
                }

                if (n == 65u) {
                    toHexString(csrFile.getdu(CSR.fcsr, CSR.CSR_M), 4U)
                }

                return when (n) {
                    66u -> toHexString(csrFile.getdu(CSR.mstatus, CSR.CSR_M), 8U)
                    67u -> toHexString(csrFile.getdu(CSR.misa, CSR.CSR_M), 8U)
                    68u -> toHexString(csrFile.getdu(CSR.mie, CSR.CSR_M), 8U)
                    69u -> toHexString(csrFile.getdu(CSR.mtvec, CSR.CSR_M), 8U)
                    70U -> toHexString(csrFile.getdu(CSR.mscratch, CSR.CSR_M), 8U)
                    71U -> toHexString(csrFile.getdu(CSR.mepc, CSR.CSR_M), 8U)
                    72U -> toHexString(csrFile.getdu(CSR.mcause, CSR.CSR_M), 8U)
                    73U -> toHexString(csrFile.getdu(CSR.mtval, CSR.CSR_M), 8U)
                    74U -> toHexString(csrFile.getdu(CSR.mip, CSR.CSR_M), 8U)
                    75u -> toHexString(csrFile.getdu(CSR.cycle, CSR.CSR_M), 8U)
                    76u -> toHexString(csrFile.getdu(CSR.time, CSR.CSR_M), 8U)
                    77u -> toHexString(csrFile.getdu(CSR.instret, CSR.CSR_M), 8U)
                    78u -> toHexString(csrFile.getdu(CSR.sstatus, CSR.CSR_M), 8U)
                    79u -> toHexString(csrFile.getdu(CSR.sie, CSR.CSR_M), 8U)
                    80U -> toHexString(csrFile.getdu(CSR.stvec, CSR.CSR_M), 8U)
                    81U -> toHexString(csrFile.getdu(CSR.sscratch, CSR.CSR_M), 8U)
                    82U -> toHexString(csrFile.getdu(CSR.sepc, CSR.CSR_M), 8U)
                    83U -> toHexString(csrFile.getdu(CSR.scause, CSR.CSR_M), 8U)
                    84U -> toHexString(csrFile.getdu(CSR.stval, CSR.CSR_M), 8U)
                    85u -> toHexString(csrFile.getdu(CSR.sip, CSR.CSR_M), 8U)
                    else -> ""
                }
            }

            'P' -> {
                val hart = machine.harts[gId]
                val gprFile = hart.gprFile
                val fprFile = hart.fprFile
                val csrFile = hart.csrFile

                val parts = payload
                    .substring(1)
                    .split("=".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()

                val n = parts[0].toUInt(0x10)
                val value: String = parts[1]

                // GPR
                if (n in 0U..31U) {
                    gprFile[n] = toLong(value)
                    return "OK"
                }

                if (n == 32U) {
                    hart.pc = toLong(value)
                    return "OK"
                }

                // FPR
                if (n in 33U..64U) {
                    fprFile[n - 33U] = toLong(value)
                    return "OK"
                }

                if (n == 65u) {
                    csrFile[CSR.fcsr, CSR.CSR_M] = toInteger(value).toULong()
                    return "OK"
                }

                return when (n) {
                    66u -> {
                        csrFile[CSR.mstatus, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    67u -> {
                        csrFile[CSR.misa, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    68u -> {
                        csrFile[CSR.mie, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    69u -> {
                        csrFile[CSR.mtvec, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    70U -> {
                        csrFile[CSR.mscratch, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    71U -> {
                        csrFile[CSR.mepc, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    72U -> {
                        csrFile[CSR.mcause, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    73U -> {
                        csrFile[CSR.mtval, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    74U -> {
                        csrFile[CSR.mip, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    75u -> {
                        csrFile[CSR.cycle, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    76u -> {
                        csrFile[CSR.time, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    77u -> {
                        csrFile[CSR.instret, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    78u -> {
                        csrFile[CSR.sstatus, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    79u -> {
                        csrFile[CSR.sie, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    80U -> {
                        csrFile[CSR.stvec, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    81U -> {
                        csrFile[CSR.sscratch, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    82U -> {
                        csrFile[CSR.sepc, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    83U -> {
                        csrFile[CSR.scause, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    84U -> {
                        csrFile[CSR.stval, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    85u -> {
                        csrFile[CSR.sip, CSR.CSR_M] = toLong(value)
                        "OK"
                    }

                    else -> ""
                }
            }

            'H' -> {
                val op = payload[1]
                val id = payload.substring(2).toInt(0x10)

                if (op == 'g') {
                    gId = id
                    return "OK"
                }

                return ""
            }

            'm' -> {
                val parts = payload
                    .substring(1)
                    .split(",".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val address = parts[0].toULong(0x10)
                val length = parts[1].toUInt(0x10)

                val data = ByteArray(length.toInt())
                machine.harts[gId].direct(data, address, false)
                return toHexString(data)
            }

            'M' -> {
                val parts = payload
                    .substring(1)
                    .split("[:,]".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val address = parts[0].toULong(0x10)
                val length = parts[1].toUInt(0x10)

                val data: ByteArray = toBytes(ByteArray(length.toInt()), parts[2])
                machine.harts[gId].direct(data, address, true)
                return "OK"
            }

            'z' -> { // remove breakpoint
                val parts = payload
                    .substring(1)
                    .split(",".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val type = parts[0].toUInt(10)
                val address = parts[1].toULong(0x10)
                val length = parts[2].toUInt(10)

                when (type) {
                    0U -> {
                        if (!breakpoints.containsKey(address)) {
                            return ""
                        }

                        val data = breakpoints[address]!!
                        machine.harts[gId].write(address, length, data.toULong(), true)
                        breakpoints.remove(address)
                        return "OK"
                    }

                    else -> {
                        Log.warn("unsupported break- or watchpoint type: '%d'", type)
                        return ""
                    }
                }
            }

            'Z' -> { // insert breakpoint
                val parts = payload
                    .substring(1)
                    .split(",".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val type = parts[0].toUInt(10)
                val address = parts[1].toULong(0x10)
                val length = parts[2].toUInt(10)

                return when (type) {
                    0U -> {
                        val data = machine.harts[gId].read(address, length, true)
                        machine.harts[gId].write(address, length, (if (length == 4U) 0x100073UL else 0x9002UL), true)
                        breakpoints[address] = data.toUInt()
                        "OK"
                    }

                    else -> {
                        Log.warn("unsupported break- or watchpoint type: '%d'", type)
                        ""
                    }
                }
            }

            'q' -> when (payload) {
                "qC" -> return "QC-1"
                "qfThreadInfo", "qsThreadInfo" -> return "l"
                else -> {
                    if (payload.startsWith("qSupported")) {
                        return "swbreak+;qXfer:features:read+;QStartNoAckMode+"
                    }
                    if (payload.startsWith("qXfer")) {
                        val parts = payload
                            .split("[:,]".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                        val `object` = parts[1]
                        val annex = parts[3]
                        val offset = parts[4].toUInt(0x10)
                        val length = parts[5].toUInt(0x10)

                        Log.info("object=%s, annex=%s, offset=%d, length=%d", `object`, annex, offset, length)

                        when (`object`) {
                            "features" -> {
                                try {
                                    ClassLoader.getSystemResourceAsStream(annex).use { stream ->
                                        if (stream == null) {
                                            throw FileNotFoundException(annex)
                                        }
                                        val skip = stream.skip(offset.toLong())
                                        if (skip.toULong() < offset) {
                                            return "l"
                                        }

                                        val data = ByteArray(length.toInt())
                                        val read = stream.read(data, 0, length.toInt())
                                        return format(
                                            "%c%s",
                                            if (read < length.toInt()) 'l' else 'm',
                                            String(data, 0, read),
                                        )
                                    }
                                } catch (e: IOException) {
                                    Log.error("failed to open resource stream: %s", e)
                                    return ""
                                }
                            }

                            else -> return ""
                        }
                    }
                    return ""
                }
            }

            'Q' -> return when (payload) {
                "QStartNoAckMode" -> {
                    state.noack = true
                    "OK"
                }

                else -> ""
            }

            'k' -> throw InterruptedException("GDB client requested to kill the process")
            else -> return ""
        }
    }

    companion object {
        private fun toHexString(value: ULong, n: UInt): String {
            val builder = StringBuilder()
            for (i in 0U..<n) {
                val b = (value shr (i shl 3).toInt()) and 0xFFUL
                builder.append(format("%02x", b))
            }
            return builder.toString()
        }

        private fun toInteger(string: String): UInt {
            var value = 0U
            for (i in 0..3) {
                val b = string.substring(i shl 1, (i + 1) shl 1).toUInt(0x10)
                value = value or (b shl (i shl 3))
            }
            return value
        }

        private fun toLong(string: String): ULong {
            var value = 0UL
            for (i in 0..7) {
                val b = string.substring(i shl 1, (i + 1) shl 1).toULong(0x10)
                value = value or (b shl (i shl 3))
            }
            return value
        }

        private fun toHexString(data: ByteArray): String {
            val builder = StringBuilder()
            for (b in data) {
                builder.append(format("%02x", b.toInt() and 0xFF))
            }
            return builder.toString()
        }

        private fun toBytes(buffer: ByteArray, string: String): ByteArray {
            var i = 0
            var j = 0
            while (i < buffer.size) {
                buffer[i] = string.substring(j, j + 2).toByte(0x10)
                ++i
                j += 2
            }
            return buffer
        }

        private fun extractw(payload: String, p: Int, consumer: Consumer<UInt>): Int {
            val string = payload.substring(p, p + 0x08)
            if (string != "xxxxxxxx") {
                consumer.accept(toInteger(string))
            }
            return p + 0x08
        }

        private fun extractd(payload: String, p: Int, consumer: Consumer<ULong>): Int {
            val string = payload.substring(p, p + 0x10)
            if (string != "xxxxxxxxxxxxxxxx") {
                consumer.accept(toLong(string))
            }
            return p + 0x10
        }
    }
}
