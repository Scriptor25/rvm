package io.scriptor.impl.device

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import io.scriptor.util.Log
import io.scriptor.util.Log.format
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream

class UART : IODevice {

    override val machine: Machine

    override val begin: ULong
    override val end: ULong

    private val inputStream: InputStream
    private val closeInput: Boolean
    private val outputStream: OutputStream
    private val closeOutput: Boolean

    private var ier = 0UL
    private var fcr = 0UL
    private var lcr = 0UL
    private var mcr = 0UL
    private var scr = 0UL

    constructor(
        machine: Machine,
        begin: ULong,
        inputStream: InputStream,
        closeInput: Boolean,
        outputStream: OutputStream,
        closeOutput: Boolean,
    ) {
        this.machine = machine
        this.begin = begin
        this.inputStream = inputStream
        this.closeInput = closeInput
        this.outputStream = outputStream
        this.closeOutput = closeOutput

        this.end = begin + 0x100UL
    }

    override fun dump(out: PrintStream) {
    }

    override fun reset() {
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        val phandle = context.get(this)

        val plic0 = context.get(machine[PLIC::class])

        builder
            .name(format("serial@%x", begin))
            .prop { it.name("phandle").data(phandle) }
            .prop { it.name("compatible").data("ns16550a") }
            .prop { it.name("reg").data(begin, end - begin) }
            .prop { it.name("clock-frequency").data(0x384000) }
            .prop { it.name("current-speed").data(0x1c200) }
            .prop { it.name("reg-shift").data(0x00) }
            .prop { it.name("reg-io-width").data(0x01) }
            .prop { it.name("interrupts").data(0x01) }
            .prop { it.name("interrupt-parent").data(plic0) }
    }

    override fun read(offset: UInt, size: UInt): ULong {
        try {
            return when (offset) {
                UART_RBR -> (if (inputStream.available() > 0) (inputStream.read() and 0xFF) else -1).toULong()
                UART_IER -> ier
                UART_IIR -> UART_IIR_INT_PENDING.toULong()
                UART_LCR -> lcr
                UART_MCR -> mcr
                UART_LSR -> (UART_LSR_THRE or UART_LSR_TEMT or (if (inputStream.available() > 0) UART_LSR_DR else 0U)).toULong()
                UART_MSR -> 0UL
                UART_SCR -> scr
                else -> {
                    Log.error("invalid uart read offset=%x, size=%d", offset, size)
                    0UL
                }
            }
        } catch (e: IOException) {
            Log.error("uart: %s", e)
            return 0UL
        }
    }

    override fun write(offset: UInt, size: UInt, value: ULong) {
        try {
            when (offset) {
                UART_THR -> {
                    outputStream.write(value.toInt() and 0xFF)
                    outputStream.flush()
                }

                UART_IER -> ier = value
                UART_FCR -> fcr = value
                UART_LCR -> lcr = value
                UART_MCR -> mcr = value
                UART_SCR -> scr = value
                else -> Log.error("invalid uart write offset=%x, size=%d, value=%x", offset, size, value)
            }
        } catch (e: IOException) {
            Log.error("uart: %s", e)
        }
    }

    override fun toString(): String {
        return format("serial@%x", begin)
    }

    override fun close() {
        if (closeInput) inputStream.close()
        if (closeOutput) outputStream.close()
    }

    companion object {
        /**
         * receiver buffer register (ro)
         */
        private const val UART_RBR = 0x00U

        /**
         * transmitter holding register (wo)
         */
        private const val UART_THR = 0x00U

        /**
         * interrupt enable register
         */
        private const val UART_IER = 0x01U

        /**
         * interrupt identification register (ro)
         */
        private const val UART_IIR = 0x02U

        /**
         * fifo control register (wo)
         */
        private const val UART_FCR = 0x02U

        /**
         * line control register
         */
        private const val UART_LCR = 0x03U

        /**
         * modem control register
         */
        private const val UART_MCR = 0x04U

        /**
         * line status register
         */
        private const val UART_LSR = 0x05U

        /**
         * modem status register
         */
        private const val UART_MSR = 0x06U

        /**
         * scratch register
         */
        private const val UART_SCR = 0x07U

        /**
         * enable received data avaible interrupt
         */
        private const val UART_IER_ERBFI = 1U

        /**
         * enable transmitter holding register empty interrupt
         */
        private const val UART_IER_ETBEI = 2U

        /**
         * enable receiver line status interrupt
         */
        private const val UART_IER_ELSI = 4U

        /**
         * enable modem status interrupt
         */
        private const val UART_IER_EDSSI = 8U

        /**
         * 0 = interrupt pending, 1 = none
         */
        private const val UART_IIR_INT_PENDING = 1U

        /**
         * interrupt id
         */
        private const val UART_IIR_INT_ID = 14U

        /**
         * fifo enabled
         */
        private const val UART_IIR_FIFO_ENABLED = 64U

        /**
         * fifo functional
         */
        private const val UART_IIR_FIFO_FUNCTIONAL = 128U

        /**
         * enable fifo mode
         */
        private const val UART_FCR_FIFO_ENABLE = 1U

        /**
         * clear received fifo
         */
        private const val UART_FCR_RX_FIFO_RESET = 2U

        /**
         * clear transmit fifo
         */
        private const val UART_FCR_TX_FIFO_RESET = 4U

        /**
         * rx trigger level
         */
        private const val UART_FCR_TRIGGER_LEVEL = 56U

        /**
         * word length (5 bits + value)
         */
        private const val UART_LCR_WORD_LENGTH = 3U

        /**
         * 
         */
        private const val UART_LCR_STOP_BITS = 4U

        /**
         * 
         */
        private const val UART_LCR_PARITY = 56U

        /**
         * force break condition
         */
        private const val UART_LCR_BREAK_CONTROL = 64U

        /**
         * divisor latch access bit
         */
        private const val UART_LCR_DLAB = 128U

        /**
         * data terminal ready
         */
        private const val UART_MCR_DTR = 1U

        /**
         * request to send
         */
        private const val UART_MCR_RTS = 2U

        /**
         * user output 1
         */
        private const val UART_MCR_OUT1 = 4U

        /**
         * user output 2
         */
        private const val UART_MCR_OUT2 = 8U

        /**
         * enable loopback test mode
         */
        private const val UART_MCR_LOOPBACK = 16U

        /**
         * data ready
         */
        private const val UART_LSR_DR = 1U

        /**
         * overrun error
         */
        private const val UART_LSR_OE = 2U

        /**
         * parity error
         */
        private const val UART_LSR_PE = 4U

        /**
         * framing error
         */
        private const val UART_LSR_FE = 8U

        /**
         * break interrupt
         */
        private const val UART_LSR_BI = 16U

        /**
         * transmitter holding register empty
         */
        private const val UART_LSR_THRE = 32U

        /**
         * transmitter empty
         */
        private const val UART_LSR_TEMT = 64U

        /**
         * error in received fifo
         */
        private const val UART_LSR_FIFO_ERROR = 128U

        /**
         * delta clear to send
         */
        private const val UART_MSR_DCTS = 1U

        /**
         * delta data set ready
         */
        private const val UART_MSR_DDSR = 2U

        /**
         * trailing edge ring indicator
         */
        private const val UART_MSR_TERI = 4U

        /**
         * delta carrier detect
         */
        private const val UART_MSR_DDCD = 8U

        /**
         * clear to send
         */
        private const val UART_MSR_CTS = 16U

        /**
         * data set ready
         */
        private const val UART_MSR_DSR = 32U

        /**
         * ring indicator
         */
        private const val UART_MSR_RI = 64U

        /**
         * carrier detect
         */
        private const val UART_MSR_DCD = 128U
    }
}
