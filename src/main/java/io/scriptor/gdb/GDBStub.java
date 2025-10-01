package io.scriptor.gdb;

import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import static io.scriptor.isa.CSR.*;

public class GDBStub implements Runnable, Closeable {

    private static @NotNull String toHexString(final long value, final int n) {
        final var builder = new StringBuilder();
        for (int i = 0; i < n; ++i) {
            final var b = (value >> (i << 3)) & 0xFF;
            builder.append("%02x".formatted(b));
        }
        return builder.toString();
    }

    private static int toInteger(final @NotNull String string) {
        var value = 0;
        for (int i = 0; i < 4; ++i) {
            final var b = Integer.parseUnsignedInt(string.substring(i << 1, (i + 1) << 1), 0x10);
            value |= (b << (i << 3));
        }
        return value;
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

    private static int extractw(final @NotNull String payload, final int p, final @NotNull IntConsumer consumer) {
        final var string = payload.substring(p, p + 0x08);
        if (!string.equals("xxxxxxxx")) {
            consumer.accept(toInteger(string));
        }
        return p + 0x08;
    }

    private static int extractd(final @NotNull String payload, final int p, final @NotNull LongConsumer consumer) {
        final var string = payload.substring(p, p + 0x10);
        if (!string.equals("xxxxxxxxxxxxxxxx")) {
            consumer.accept(toLong(string));
        }
        return p + 0x10;
    }

    private final Machine machine;

    private final ServerSocketChannel channel;
    private final Selector selector;

    private SocketChannel client;

    private int gId = 0;

    private final Map<Long, Long> breakpoints = new HashMap<>();

    public GDBStub(final @NotNull Machine machine, final int port) throws IOException {
        this.machine = machine;

        machine.onBreakpoint(id -> {
            machine.pause();
            stop(id, 0x05);
        });
        machine.onTrap(id -> {
            machine.pause();
            stop(id, 0x05);
        });

        channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress(port));

        selector = Selector.open();
        channel.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    public void run() {
        while (true) {
            try {
                selector.select();

                for (var it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    final var key = it.next();
                    it.remove();

                    if (key.isAcceptable()) {
                        handleAccept(key);
                        continue;
                    }

                    handleRead(key);
                }
            } catch (final IOException e) {
                Log.error("gdb error: %s", e);
            } catch (final InterruptedException e) {
                Log.warn("gdb thread interrupted: %s", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop(final int id, final int code) {
        try {
            writePacket(client, "T%02xcore:%x;".formatted(code, id));
        } catch (final IOException e) {
            Log.error("gdb: %s", e);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private void handleAccept(final @NotNull SelectionKey key) throws IOException {
        client = channel.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new ClientState());
        Log.info("gdb: accepted connection from %s", client.getRemoteAddress());
    }

    private void handleRead(final @NotNull SelectionKey key) throws IOException, InterruptedException {
        final var client = (SocketChannel) key.channel();
        final var state  = (ClientState) key.attachment();

        final var read = client.read(state.buffer);
        if (read < 0) {
            client.close();
            return;
        }

        state.buffer.flip();
        while (state.buffer.hasRemaining()) {
            final var b = state.buffer.get() & 0xFF;
            process(b, client, state);
        }
        state.buffer.compact();
    }

    private void process(int b, final @NotNull SocketChannel client, final @NotNull ClientState state)
            throws IOException, InterruptedException {

        if (state.append) {
            if (b == '#') {
                state.checksum &= 0xFF;
                state.append = false;
                state.end = true;
                state.packetChecksum.setLength(0);
                return;
            }

            state.packet.append((char) b);
            state.checksum = state.checksum + b;
            return;
        }

        if (state.end) {
            state.packetChecksum.append((char) b);
            if (state.packetChecksum.length() != 2) {
                return;
            }

            state.end = false;

            final var checksum = Integer.parseUnsignedInt(state.packetChecksum.toString(), 0x10);
            if (checksum != state.checksum)
                throw new IOException("checksum mismatch: %02x != %02x".formatted(checksum, state.checksum));

            writeRaw(client, "+");

            final var request = state.packet.toString();
            Log.info("--> %s", request);
            final var response = handle(request);
            Log.info("<-- %s", response);
            writePacket(client, response);
            return;
        }

        if (b == '+') {
            // acknowledge
            return;
        }

        if (b == '-') {
            // resend
            return;
        }

        if (b == 0x03) {
            // interrupt
            machine.pause();
            writePacket(client, "S02");
            return;
        }

        if (b == '$') {
            // payload begin
            state.append = true;
            state.checksum = 0;
            state.packet.setLength(0);
            return;
        }

        Log.info("unhandled request byte: %02x (%c)", b, b <= 0x20 ? ' ' : (char) b);
    }

    private void writeRaw(final @NotNull SocketChannel client, final @NotNull String data) throws IOException {
        final var bytes  = data.getBytes();
        final var buffer = ByteBuffer.wrap(bytes);
        client.write(buffer);
    }

    private void writePacket(final @NotNull SocketChannel client, final @NotNull String payload) throws IOException {
        var checksum = 0;
        for (final var b : payload.getBytes()) {
            checksum = (checksum + b) & 0xFF;
        }

        final var packet = "$%s#%02x".formatted(payload, checksum);
        final var buffer = ByteBuffer.wrap(packet.getBytes());
        client.write(buffer);
    }

    private @NotNull String handle(final @NotNull String payload) throws InterruptedException {
        return switch (payload.charAt(0)) {
            case '?' -> {
                // S05 -> SIGTRAP
                // S0b -> SIGSEGV
                // S04 -> SIGILL
                // S09 -> SIGKILL
                // S00 -> no signal
                yield "S05";
            }
            case 'c' -> {
                machine.spin();
                yield "OK";
            }
            case 's' -> {
                machine.step();
                yield "OK";
            }
            case 'D' -> {
                machine.reset();
                yield "OK";
            }
            case 'g' -> {
                final var hart    = machine.hart(gId);
                final var gprFile = hart.gprFile();
                final var fprFile = hart.fprFile();
                final var csrFile = hart.csrFile();
                final var pc      = hart.pc();

                final var response = new StringBuilder();

                // GPR

                for (int i = 0; i < 32; ++i) {
                    response.append(toHexString(gprFile.getd(i), 8));
                }

                response.append(toHexString(pc, 8));

                // FPR

                for (int i = 0; i < 32; ++i) {
                    response.append(toHexString(fprFile.getdr(i), 8));
                }

                response.append(toHexString(csrFile.getw(fcsr), 4));

                // CSR

                response.append(toHexString(csrFile.getd(mstatus), 8));
                response.append(toHexString(csrFile.getd(misa), 8));
                response.append(toHexString(csrFile.getd(mie), 8));
                response.append(toHexString(csrFile.getd(mtvec), 8));
                response.append(toHexString(csrFile.getd(mscratch), 8));
                response.append(toHexString(csrFile.getd(mepc), 8));
                response.append(toHexString(csrFile.getd(mcause), 8));
                response.append(toHexString(csrFile.getd(mtval), 8));
                response.append(toHexString(csrFile.getd(mip), 8));
                response.append(toHexString(csrFile.getd(cycle), 8));
                response.append(toHexString(csrFile.getd(time), 8));
                response.append(toHexString(csrFile.getd(instret), 8));
                response.append(toHexString(csrFile.getd(sstatus), 8));
                response.append(toHexString(csrFile.getd(sie), 8));
                response.append(toHexString(csrFile.getd(stvec), 8));
                response.append(toHexString(csrFile.getd(sscratch), 8));
                response.append(toHexString(csrFile.getd(sepc), 8));
                response.append(toHexString(csrFile.getd(scause), 8));
                response.append(toHexString(csrFile.getd(stval), 8));
                response.append(toHexString(csrFile.getd(sip), 8));

                yield response.toString();
            }
            case 'G' -> {
                final var hart    = machine.hart(gId);
                final var gprFile = hart.gprFile();
                final var fprFile = hart.fprFile();
                final var csrFile = hart.csrFile();

                int p = 1;

                // GPR

                for (int i = 0; i < 32; ++i) {
                    final var reg = i;
                    p = extractd(payload, p, x -> gprFile.putd(reg, x));
                }

                p = extractd(payload, p, hart::pc);

                // FPR

                for (int i = 0; i < 32; ++i) {
                    final var reg = i;
                    p = extractd(payload, p, x -> fprFile.putdr(reg, x));
                }

                p = extractw(payload, p, x -> csrFile.putw(fcsr, x));

                // CSR

                p = extractd(payload, p, x -> csrFile.putd(mstatus, x));
                p = extractd(payload, p, x -> csrFile.putd(misa, x));
                p = extractd(payload, p, x -> csrFile.putd(mie, x));
                p = extractd(payload, p, x -> csrFile.putd(mtvec, x));
                p = extractd(payload, p, x -> csrFile.putd(mscratch, x));
                p = extractd(payload, p, x -> csrFile.putd(mepc, x));
                p = extractd(payload, p, x -> csrFile.putd(mcause, x));
                p = extractd(payload, p, x -> csrFile.putd(mtval, x));
                p = extractd(payload, p, x -> csrFile.putd(mip, x));
                p = extractd(payload, p, x -> csrFile.putd(cycle, x));
                p = extractd(payload, p, x -> csrFile.putd(time, x));
                p = extractd(payload, p, x -> csrFile.putd(instret, x));
                p = extractd(payload, p, x -> csrFile.putd(sstatus, x));
                p = extractd(payload, p, x -> csrFile.putd(sie, x));
                p = extractd(payload, p, x -> csrFile.putd(stvec, x));
                p = extractd(payload, p, x -> csrFile.putd(sscratch, x));
                p = extractd(payload, p, x -> csrFile.putd(sepc, x));
                p = extractd(payload, p, x -> csrFile.putd(scause, x));
                p = extractd(payload, p, x -> csrFile.putd(stval, x));
                p = extractd(payload, p, x -> csrFile.putd(sip, x));

                yield "OK";
            }
            case 'p' -> {
                final var hart    = machine.hart(gId);
                final var gprFile = hart.gprFile();
                final var fprFile = hart.fprFile();
                final var csrFile = hart.csrFile();
                final var pc      = hart.pc();

                final var n = Integer.parseUnsignedInt(payload.substring(1), 0x10);

                // GPR

                if (0 <= n && n <= 31) {
                    yield toHexString(gprFile.getd(n), 8);
                }

                if (n == 32) {
                    yield toHexString(pc, 8);
                }

                // FPR

                if (33 <= n && n <= 64) {
                    yield toHexString(fprFile.getdr(n - 33), 8);
                }

                if (n == 65) {
                    yield toHexString(csrFile.getw(fcsr), 4);
                }

                // CSR

                yield switch (n) {
                    case 66 -> toHexString(csrFile.getd(mstatus), 8);
                    case 67 -> toHexString(csrFile.getd(misa), 8);
                    case 68 -> toHexString(csrFile.getd(mie), 8);
                    case 69 -> toHexString(csrFile.getd(mtvec), 8);
                    case 70 -> toHexString(csrFile.getd(mscratch), 8);
                    case 71 -> toHexString(csrFile.getd(mepc), 8);
                    case 72 -> toHexString(csrFile.getd(mcause), 8);
                    case 73 -> toHexString(csrFile.getd(mtval), 8);
                    case 74 -> toHexString(csrFile.getd(mip), 8);
                    case 75 -> toHexString(csrFile.getd(cycle), 8);
                    case 76 -> toHexString(csrFile.getd(time), 8);
                    case 77 -> toHexString(csrFile.getd(instret), 8);
                    case 78 -> toHexString(csrFile.getd(sstatus), 8);
                    case 79 -> toHexString(csrFile.getd(sie), 8);
                    case 80 -> toHexString(csrFile.getd(stvec), 8);
                    case 81 -> toHexString(csrFile.getd(sscratch), 8);
                    case 82 -> toHexString(csrFile.getd(sepc), 8);
                    case 83 -> toHexString(csrFile.getd(scause), 8);
                    case 84 -> toHexString(csrFile.getd(stval), 8);
                    case 85 -> toHexString(csrFile.getd(sip), 8);
                    default -> "";
                };
            }
            case 'P' -> {
                final var hart    = machine.hart(gId);
                final var gprFile = hart.gprFile();
                final var fprFile = hart.fprFile();
                final var csrFile = hart.csrFile();

                final var parts = payload.substring(1).split("=");

                final var n     = Integer.parseUnsignedInt(parts[0], 0x10);
                final var value = parts[1];

                // GPR

                if (0 <= n && n <= 31) {
                    gprFile.putd(n, toLong(value));
                    yield "OK";
                }

                if (n == 32) {
                    hart.pc(toLong(value));
                    yield "OK";
                }

                // FPR

                if (33 <= n && n <= 64) {
                    fprFile.putdr(n - 33, toLong(value));
                    yield "OK";
                }

                if (n == 65) {
                    csrFile.putw(fcsr, toInteger(value));
                    yield "OK";
                }

                // CSR

                yield switch (n) {
                    case 66 -> {
                        csrFile.putd(mstatus, toLong(value));
                        yield "OK";
                    }
                    case 67 -> {
                        csrFile.putd(misa, toLong(value));
                        yield "OK";
                    }
                    case 68 -> {
                        csrFile.putd(mie, toLong(value));
                        yield "OK";
                    }
                    case 69 -> {
                        csrFile.putd(mtvec, toLong(value));
                        yield "OK";
                    }
                    case 70 -> {
                        csrFile.putd(mscratch, toLong(value));
                        yield "OK";
                    }
                    case 71 -> {
                        csrFile.putd(mepc, toLong(value));
                        yield "OK";
                    }
                    case 72 -> {
                        csrFile.putd(mcause, toLong(value));
                        yield "OK";
                    }
                    case 73 -> {
                        csrFile.putd(mtval, toLong(value));
                        yield "OK";
                    }
                    case 74 -> {
                        csrFile.putd(mip, toLong(value));
                        yield "OK";
                    }
                    case 75 -> {
                        csrFile.putd(cycle, toLong(value));
                        yield "OK";
                    }
                    case 76 -> {
                        csrFile.putd(time, toLong(value));
                        yield "OK";
                    }
                    case 77 -> {
                        csrFile.putd(instret, toLong(value));
                        yield "OK";
                    }
                    case 78 -> {
                        csrFile.putd(sstatus, toLong(value));
                        yield "OK";
                    }
                    case 79 -> {
                        csrFile.putd(sie, toLong(value));
                        yield "OK";
                    }
                    case 80 -> {
                        csrFile.putd(stvec, toLong(value));
                        yield "OK";
                    }
                    case 81 -> {
                        csrFile.putd(sscratch, toLong(value));
                        yield "OK";
                    }
                    case 82 -> {
                        csrFile.putd(sepc, toLong(value));
                        yield "OK";
                    }
                    case 83 -> {
                        csrFile.putd(scause, toLong(value));
                        yield "OK";
                    }
                    case 84 -> {
                        csrFile.putd(stval, toLong(value));
                        yield "OK";
                    }
                    case 85 -> {
                        csrFile.putd(sip, toLong(value));
                        yield "OK";
                    }
                    default -> "";
                };
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
                machine.direct(address, data, false);
                yield toHexString(data);
            }
            case 'M' -> {
                final var parts   = payload.substring(1).split("[:,]");
                final var address = Long.parseUnsignedLong(parts[0], 0x10);
                final var length  = Integer.parseUnsignedInt(parts[1], 0x10);

                final var data = toBytes(new byte[length], parts[2]);
                if (machine.direct(address, data, true)) {
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
                        if (!breakpoints.containsKey(address)) {
                            yield "E00";
                        }
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
                case "qOffsets" -> "Text=%1$08x;Data=%1$08x;Bss=%1$08x".formatted(machine.offset());
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
                                    Log.error("failed to open resource stream: %s", e);
                                    yield "";
                                }
                            }
                            default -> "";
                        };
                    }
                    yield "";
                }
            };
            case 'k' -> throw new InterruptedException("GDB client requested to kill the process");
            default -> "";
        };
    }
}
