package io.scriptor.impl.device;

import io.scriptor.machine.IODevice;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public final class UART implements IODevice {

    private static final int TX_OFFSET = 0x00;
    private static final int RX_OFFSET = 0x04;
    private static final int STATUS_OFFSET = 0x08;

    private static final int TX_READY = 0b01;
    private static final int RX_READY = 0b10;

    private final InputStream in;
    private final OutputStream out;

    public UART(final @NotNull InputStream in, final @NotNull OutputStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
    }

    @Override
    public void reset() {
    }

    @Override
    public long read(final long offset, final int size) {
        try {
            return switch ((int) offset) {
                case RX_OFFSET -> in.available() > 0 ? (in.read() & 0xFFL) : -1L;
                case STATUS_OFFSET -> TX_READY | (in.available() > 0 ? RX_READY : 0);
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
    public void write(final long offset, final int size, final long value) {
        try {
            switch ((int) offset) {
                case TX_OFFSET -> {
                    out.write((int) (value & 0xFFL));
                    out.flush();
                }
                default -> Log.error("invalid uart write offset=%x, size=%d, value=%x", offset, size, value);
            }
        } catch (final IOException e) {
            Log.error("uart: %s", e);
        }
    }
}
