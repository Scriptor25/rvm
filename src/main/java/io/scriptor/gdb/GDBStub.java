package io.scriptor.gdb;

import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public class GDBStub implements Runnable, Closeable {

    private static @NotNull String toHexString(final long value) {
        final var builder = new StringBuilder();
        for (int i = 0; i < 8; ++i) {
            final var b = (value >> (i << 3)) & 0xFF;
            builder.append("%02x".formatted(b));
        }
        return builder.toString();
    }

    private static long toLong(final @NotNull String string) {
        var value = 0L;
        for (int i = 0; i < 8; ++i) {
            final var b = Long.parseUnsignedLong(string.substring(i << 1, (i + 1) << 1), 0x10);
            value |= (b << (i << 3));
        }
        return value;
    }

    private static @NotNull String toHexString(final byte @NotNull [] data) {
        final var builder = new StringBuilder();
        for (final var b : data) {
            builder.append("%02x".formatted(b & 0xFF));
        }
        return builder.toString();
    }

    private static byte @NotNull [] toBytes(final byte @NotNull [] buffer, final @NotNull String string) {
        for (int i = 0, j = 0; i < buffer.length; ++i, j += 2) {
            buffer[i] = Byte.parseByte(string.substring(j, j + 2), 0x10);
        }
        return buffer;
    }

    private final Machine machine;

    private final ServerSocket server;
    private InputStream in;
    private OutputStream out;

    private int gId = 0;

    private final Map<Long, Long> breakpoints = new HashMap<>();

    public GDBStub(final @NotNull Machine machine, final int port) throws IOException {
        this.machine = machine;

        machine.breakpoint(id -> {
            try {
                machine.pause();
                final var payload = "T%02xcore:%x;".formatted(0x05, id);
                writePacket(payload);
            } catch (final IOException e) {
                Log.warn("gdb: %s", e);
            }
        });

        server = new ServerSocket(port);
    }

    @Override
    public void run() {
        while (true) {
            try (final var client = server.accept()) {
                while (client.isConnected()) {
                    in = client.getInputStream();
                    out = client.getOutputStream();

                    final var request = readPacket();
                    Log.info("--> %s", request);
                    final var response = handle(request);
                    Log.info("<-- %s", response);
                    writePacket(response);
                }
            } catch (final IOException e) {
                Log.warn("gdb: %s", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    private @NotNull String readPacket()
            throws IOException {
        int b;
        do {
            b = in.read();
        } while (b != '$');

        final var payload = new StringBuilder();

        int computed = 0;
        while ((b = in.read()) != '#') {
            payload.append((char) b);
            computed = (computed + b) & 0xFF;
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
            checksum = (checksum + b) & 0xFF;

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
                machine.spin();
                yield "OK";
            }
            case 's' -> {
                machine.step();
                yield "OK";
            }
            case 'r', 'R' -> {
                machine.reset();
                yield "OK";
            }
            case 'g' -> {
                final var hart    = machine.hart(gId);
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
                final var hart    = machine.hart(gId);
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

                if (op == 'g') {
                    gId = id;
                    yield "OK";
                }

                yield "";
            }
            case 'm' -> {
                final var parts   = payload.substring(1).split(",");
                final var address = Long.parseUnsignedLong(parts[0], 0x10);
                final var length  = Integer.parseUnsignedInt(parts[1], 0x10);

                final var data = new byte[length];
                machine.direct(false, address, data);
                yield toHexString(data);
            }
            case 'M' -> {
                final var parts   = payload.substring(1).split("[:,]");
                final var address = Long.parseUnsignedLong(parts[0], 0x10);
                final var length  = Integer.parseUnsignedInt(parts[1], 0x10);

                final var data = toBytes(new byte[length], parts[2]);
                if (machine.direct(true, address, data)) {
                    yield "OK";
                }
                yield "E01";
            }
            // break/watchpoint type:
            //  0 -> software breakpoint
            //  1 -> hardware breakpoint
            //  2 -> write watchpoint
            //  3 -> read watchpoint
            //  4 -> access watchpoint
            case 'z' -> { // remove breakpoint
                final var parts   = payload.substring(1).split(",");
                final var type    = Integer.parseUnsignedInt(parts[0], 10);
                final var address = Long.parseUnsignedLong(parts[1], 0x10);
                final var length  = Integer.parseUnsignedInt(parts[2], 10);

                yield switch (type) {
                    case 0 -> {
                        final var data = breakpoints.get(address);
                        machine.write(address, length, data, true);
                        breakpoints.remove(address);
                        yield "OK";
                    }
                    default -> {
                        Log.warn("unsupported break- or watchpoint type: '%d'".formatted(type));
                        yield "";
                    }
                };
            }
            case 'Z' -> { // insert breakpoint
                final var parts   = payload.substring(1).split(",");
                final var type    = Integer.parseUnsignedInt(parts[0], 10);
                final var address = Long.parseUnsignedLong(parts[1], 0x10);
                final var length  = Integer.parseUnsignedInt(parts[2], 10);

                yield switch (type) {
                    case 0 -> {
                        final var data = machine.read(address, length, true);
                        machine.write(address, length, length == 4 ? 0x100073 : 0x9002, true);
                        breakpoints.put(address, data);
                        yield "OK";
                    }
                    default -> {
                        Log.warn("unsupported break- or watchpoint type: '%d'".formatted(type));
                        yield "";
                    }
                };
            }
            case 'q' -> switch (payload) {
                case "qC" -> "QC-1";
                case "qfThreadInfo", "qsThreadInfo" -> "l";
                case "qOffsets" -> "Text=%1$08x;Data=%1$08x;Bss=%1$08x".formatted(0x80000000);
                default -> {
                    if (payload.startsWith("qSupported")) {
                        yield "swbreak+;qXfer:features:read+";
                    }
                    if (payload.startsWith("qXfer")) {
                        final var parts  = payload.split("[:,]");
                        final var object = parts[1];
                        final var annex  = parts[3];
                        final var offset = Integer.parseUnsignedInt(parts[4], 0x10);
                        final var length = Integer.parseUnsignedInt(parts[5], 0x10);
                        Log.info("object=%s, annex=%s, offset=%d, length=%d", object, annex, offset, length);
                        yield switch (object) {
                            case "features" -> {
                                try (final var stream = ClassLoader.getSystemResourceAsStream(annex)) {
                                    if (stream == null) {
                                        throw new FileNotFoundException(annex);
                                    }
                                    final var skip = stream.skip(offset);
                                    if (skip < offset) {
                                        yield "l";
                                    }
                                    final var data = new byte[length];
                                    final var read = stream.read(data, 0, length);
                                    yield "%c%s".formatted(read < length ? 'l' : 'm', new String(data, 0, read));
                                } catch (final IOException e) {
                                    Log.warn("failed to open resource stream: %s", e);
                                    yield "";
                                }
                            }
                            default -> "";
                        };
                    }
                    yield "";
                }
            };
            case 'k' -> throw new RuntimeException("GDB client requested to kill the process");
            default -> "";
        };
    }
}
