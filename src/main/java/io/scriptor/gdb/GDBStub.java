package io.scriptor.gdb;

import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class GDBStub implements Runnable, Closeable {

    private static @NotNull String toHexString(final long value) {
        final var builder = new StringBuilder();
        for (int i = 0; i < 8; ++i) {
            final var b = (value >> (i << 3)) & 0xff;
            builder.append("%02x".formatted(b));
        }
        return builder.toString();
    }

    private static long toLong(final @NotNull String str) {
        var value = 0L;
        for (int i = 0; i < 8; ++i) {
            final var b = Long.parseUnsignedLong(str.substring(i << 1, (i + 1) << 1), 0x10);
            value |= (b << (i << 3));
        }
        return value;
    }

    private static @NotNull String toHexString(final byte @NotNull [] data) {
        final var builder = new StringBuilder();
        for (final var b : data) {
            builder.append("%02x".formatted(b & 0xff));
        }
        return builder.toString();
    }

    private static byte @NotNull [] toBytes(final @NotNull String str) {
        final var data = new byte[str.length() / 2];
        for (int i = 0, j = 0; i < data.length; ++i, j += 2) {
            data[i] = (byte) Integer.parseInt(str.substring(j, j + 2), 0x10);
        }
        return data;
    }

    private final Machine machine;

    private final ServerSocket server;
    private final Socket client;
    private final InputStream in;
    private final OutputStream out;

    private int gThread = 0;
    private int cThread = -1;

    public GDBStub(final @NotNull Machine machine, final int port) throws IOException {
        this.machine = machine;

        server = new ServerSocket(port);
        client = server.accept();
        in = client.getInputStream();
        out = client.getOutputStream();
    }

    @Override
    public void run() {
        try {
            while (client.isConnected() && !client.isClosed()) {
                final var request  = readPacket();
                final var response = handle(request);
                writePacket(response);
            }
        } catch (final IOException e) {
            Log.warn("gdb: %s", e);
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    private @NotNull String readPacket() throws IOException {
        int b;
        do {
            b = in.read();
        } while (b != '$');

        final var payload = new StringBuilder();

        int computed = 0;
        while ((b = in.read()) != '#') {
            payload.append((char) b);
            computed = (computed + b) & 0xff;
        }

        final var checksum = Integer.parseUnsignedInt("%c%c".formatted(in.read(), in.read()), 0x10);

        if (checksum != computed)
            throw new IOException("checksum mismatch: %02x (computed) != %02x (checksum)"
                                          .formatted(computed, checksum));

        out.write('+');
        return payload.toString();
    }

    private void writePacket(final @NotNull String payload) throws IOException {
        var checksum = 0;
        for (final var b : payload.getBytes())
            checksum = (checksum + b) & 0xff;

        final var packet = "$%s#%02x".formatted(payload, checksum);
        out.write(packet.getBytes());
        out.flush();
    }

    private @NotNull String handle(final @NotNull String payload) {
        return switch (payload.charAt(0)) {
            case '?' -> {
                // S05 -> SIGTRAP
                // S0b -> SIGSEGV
                // S04 -> SIGILL
                // S09 -> SIGKILL
                // S00 -> no signal
                yield "S00";
            }
            case 'c' -> {
                machine.spin(cThread);
                yield "OK";
            }
            case 'g' -> {
                final var hart    = machine.getHart(gThread);
                final var gprFile = hart.gprFile();
                final var pc      = hart.pc();

                final var out = new StringBuilder();
                for (int reg = 0; reg < 32; ++reg) {
                    out.append(toHexString(gprFile.getd(reg)));
                }

                out.append(toHexString(pc));

                yield out.toString();
            }
            case 'G' -> {
                final var hart    = machine.getHart(gThread);
                final var gprFile = hart.gprFile();

                int p = 1;
                for (int i = 0; i < 32; ++i) {
                    final var str = payload.substring(p, p += 0x10);
                    if (!str.equals("xxxxxxxxxxxxxxxx")) {
                        gprFile.putd(i, toLong(str));
                    }
                }

                {
                    final var str = payload.substring(p, p += 0x10);
                    if (!str.equals("xxxxxxxxxxxxxxxx")) {
                        hart.pc(toLong(str));
                    }
                }

                yield "OK";
            }
            case 'H' -> {
                final var op = payload.charAt(1);
                final var id = Integer.parseInt(payload.substring(2), 0x10);

                yield switch (op) {
                    case 'g' -> {
                        gThread = id;
                        yield "OK";
                    }
                    case 'c' -> {
                        cThread = id;
                        yield "OK";
                    }
                    default -> "";
                };
            }
            case 'k' -> throw new RuntimeException("GDB client requested to kill process");
            case 'm' -> {
                final var parts   = payload.substring(1).split(",");
                final var address = Long.parseUnsignedLong(parts[0], 0x10);
                final var length  = Integer.parseUnsignedInt(parts[1], 0x10);

                final var data = new byte[length];
                machine.read(address, data, 0, length);
                yield toHexString(data);
            }
            case 'M' -> {
                final var parts   = payload.substring(1).split("[:,]");
                final var address = Long.parseUnsignedLong(parts[0], 0x10);
                final var length  = Integer.parseUnsignedInt(parts[1], 0x10);

                final var str  = parts[2];
                final var data = toBytes(str);

                machine.write(address, data, 0, length);
                yield "OK";
            }
            case 'r', 'R' -> {
                machine.reset();
                yield "OK";
            }
            case 's' -> {
                machine.step(cThread);
                yield "OK";
            }
            // break/watchpoint type:
            //  0 -> software breakpoint
            //  1 -> hardware breakpoint
            //  2 -> write watchpoint
            //  3 -> read watchpoint
            //  4 -> access watchpoint
            case 'z' -> { // remove breakpoint
                final var parts = payload.substring(1).split(",");
                final var type  = parts[0];
                final var addr  = Long.parseUnsignedLong(parts[1], 0x10);
                final var kind  = parts[2];

                yield switch (type) {
                    case "0" -> {
                        machine.removeBreakpoint(addr);
                        yield "OK";
                    }
                    default -> {
                        Log.warn("unsupported break- or watchpoint type: '%s'".formatted(type));
                        yield "";
                    }
                };
            }
            case 'Z' -> { // insert breakpoint
                final var parts = payload.substring(1).split(",");
                final var type  = parts[0];
                final var addr  = Long.parseUnsignedLong(parts[1], 0x10);
                final var kind  = parts[2];

                yield switch (type) {
                    case "0" -> {
                        machine.insertBreakpoint(addr, thread -> {
                            try {
                                machine.pause(-1);
                                writePacket("T%02xthread:%x;".formatted(0x05, thread));
                            } catch (final IOException e) {
                                Log.warn("gdb: %s", e);
                            }
                        });
                        yield "OK";
                    }
                    default -> {
                        Log.warn("unsupported break- or watchpoint type: '%s'".formatted(type));
                        yield "";
                    }
                };
            }
            default -> {
                Log.warn("unsupported gdb payload: '%s'", payload);
                yield "";
            }
        };
    }
}
