package io.scriptor.impl.device;

import io.scriptor.fdt.BuilderContext;
import io.scriptor.fdt.NodeBuilder;
import io.scriptor.machine.Device;
import io.scriptor.machine.IODevice;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public final class UART implements IODevice {

    /**
     * receiver buffer register [ro]
     */
    private static final int UART_RBR = 0x00;
    /**
     * transmitter holding register [wo]
     */
    private static final int UART_THR = 0x00;
    /**
     * interrupt enable register
     */
    private static final int UART_IER = 0x01;
    /**
     * interrupt identification register [ro]
     */
    private static final int UART_IIR = 0x02;
    /**
     * fifo control register [wo]
     */
    private static final int UART_FCR = 0x02;
    /**
     * line control register
     */
    private static final int UART_LCR = 0x03;
    /**
     * modem control register
     */
    private static final int UART_MCR = 0x04;
    /**
     * line status register
     */
    private static final int UART_LSR = 0x05;
    /**
     * modem status register
     */
    private static final int UART_MSR = 0x06;
    /**
     * scratch register
     */
    private static final int UART_SCR = 0x07;

    /**
     * enable received data avaible interrupt
     */
    private static final int UART_IER_ERBFI = 0b00000001;
    /**
     * enable transmitter holding register empty interrupt
     */
    private static final int UART_IER_ETBEI = 0b00000010;
    /**
     * enable receiver line status interrupt
     */
    private static final int UART_IER_ELSI = 0b00000100;
    /**
     * enable modem status interrupt
     */
    private static final int UART_IER_EDSSI = 0b00001000;

    /**
     * 0 = interrupt pending, 1 = none
     */
    private static final int UART_IIR_INT_PENDING = 0b00000001;
    /**
     * interrupt id
     */
    private static final int UART_IIR_INT_ID = 0b00001110;
    /**
     * fifo enabled
     */
    private static final int UART_IIR_FIFO_ENABLED = 0b01000000;
    /**
     * fifo functional
     */
    private static final int UART_IIR_FIFO_FUNCTIONAL = 0b10000000;

    /**
     * enable fifo mode
     */
    private static final int UART_FCR_FIFO_ENABLE = 0b00000001;
    /**
     * clear received fifo
     */
    private static final int UART_FCR_RX_FIFO_RESET = 0b00000010;
    /**
     * clear transmit fifo
     */
    private static final int UART_FCR_TX_FIFO_RESET = 0b00000100;
    /**
     * rx trigger level
     */
    private static final int UART_FCR_TRIGGER_LEVEL = 0b00111000;

    /**
     * word length (5 bits + value)
     */
    private static final int UART_LCR_WORD_LENGTH = 0b00000011;
    /**
     *
     */
    private static final int UART_LCR_STOP_BITS = 0b00000100;
    /**
     *
     */
    private static final int UART_LCR_PARITY = 0b00111000;
    /**
     * force break condition
     */
    private static final int UART_LCR_BREAK_CONTROL = 0b01000000;
    /**
     * divisor latch access bit
     */
    private static final int UART_LCR_DLAB = 0b10000000;

    /**
     * data terminal ready
     */
    private static final int UART_MCR_DTR = 0b00000001;
    /**
     * request to send
     */
    private static final int UART_MCR_RTS = 0b00000010;
    /**
     * user output 1
     */
    private static final int UART_MCR_OUT1 = 0b00000100;
    /**
     * user output 2
     */
    private static final int UART_MCR_OUT2 = 0b00001000;
    /**
     * enable loopback test mode
     */
    private static final int UART_MCR_LOOPBACK = 0b00010000;

    /**
     * data ready
     */
    private static final int UART_LSR_DR = 0b00000001;
    /**
     * overrun error
     */
    private static final int UART_LSR_OE = 0b00000010;
    /**
     * parity error
     */
    private static final int UART_LSR_PE = 0b00000100;
    /**
     * framing error
     */
    private static final int UART_LSR_FE = 0b00001000;
    /**
     * break interrupt
     */
    private static final int UART_LSR_BI = 0b00010000;
    /**
     * transmitter holding register empty
     */
    private static final int UART_LSR_THRE = 0b00100000;
    /**
     * transmitter empty
     */
    private static final int UART_LSR_TEMT = 0b01000000;
    /**
     * error in received fifo
     */
    private static final int UART_LSR_FIFO_ERROR = 0b10000000;

    /**
     * delta clear to send
     */
    private static final int UART_MSR_DCTS = 0b00000001;
    /**
     * delta data set ready
     */
    private static final int UART_MSR_DDSR = 0b00000010;
    /**
     * trailing edge ring indicator
     */
    private static final int UART_MSR_TERI = 0b00000100;
    /**
     * delta carrier detect
     */
    private static final int UART_MSR_DDCD = 0b00001000;
    /**
     * clear to send
     */
    private static final int UART_MSR_CTS = 0b00010000;
    /**
     * data set ready
     */
    private static final int UART_MSR_DSR = 0b00100000;
    /**
     * ring indicator
     */
    private static final int UART_MSR_RI = 0b01000000;
    /**
     * carrier detect
     */
    private static final int UART_MSR_DCD = 0b10000000;


    private final Machine machine;
    private final long begin;
    private final long end;
    private final InputStream in;
    private final OutputStream out;

    private long ier, fcr, lcr, mcr, scr;

    public UART(
            final @NotNull Machine machine,
            final long begin,
            final @NotNull InputStream in,
            final @NotNull OutputStream out
    ) {
        this.machine = machine;
        this.begin = begin;
        this.end = begin + 0x100L;
        this.in = in;
        this.out = out;
    }

    @Override
    public @NotNull Machine machine() {
        return machine;
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void build(final @NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
        final var phandle = context.push(this);

        final var plic0 = context.get(machine.device(PLIC.class));

        builder.name("serial@%x".formatted(begin))
               .prop(pb -> pb.name("phandle").data(phandle))
               .prop(pb -> pb.name("compatible").data("ns16550a"))
               .prop(pb -> pb.name("reg").data(begin, end - begin))
               .prop(pb -> pb.name("clock-frequency").data(0x384000))
               .prop(pb -> pb.name("current-speed").data(0x1c200))
               .prop(pb -> pb.name("reg-shift").data(0x00))
               .prop(pb -> pb.name("reg-io-width").data(0x01))
               .prop(pb -> pb.name("interrupts").data(0x01))
               .prop(pb -> pb.name("interrupt-parent").data(plic0));
    }

    @Override
    public long begin() {
        return begin;
    }

    @Override
    public long end() {
        return end;
    }

    @Override
    public long read(final int offset, final int size) {
        try {
            return switch (offset) {
                case UART_RBR -> in.available() > 0 ? (in.read() & 0xFFL) : -1L;
                case UART_IER -> ier;
                case UART_IIR -> UART_IIR_INT_PENDING;
                case UART_LCR -> lcr;
                case UART_MCR -> mcr;
                case UART_LSR -> UART_LSR_THRE | UART_LSR_TEMT | (in.available() > 0 ? UART_LSR_DR : 0);
                case UART_MSR -> 0L;
                case UART_SCR -> scr;
                default -> {
                    Log.error("invalid uart read offset=%x, size=%d", offset, size);
                    yield 0L;
                }
            };
        } catch (final IOException e) {
            Log.error("uart: %s", e);
            return 0L;
        }
    }

    @Override
    public void write(final int offset, final int size, final long value) {
        try {
            switch (offset) {
                case UART_THR -> {
                    out.write((int) (value & 0xFFL));
                    out.flush();
                }
                case UART_IER -> ier = value;
                case UART_FCR -> fcr = value;
                case UART_LCR -> lcr = value;
                case UART_MCR -> mcr = value;
                case UART_SCR -> scr = value;
                default -> Log.error("invalid uart write offset=%x, size=%d, value=%x", offset, size, value);
            }
        } catch (final IOException e) {
            Log.error("uart: %s", e);
        }
    }

    @Override
    public @NotNull String toString() {
        return "serial@%x".formatted(begin);
    }
}
