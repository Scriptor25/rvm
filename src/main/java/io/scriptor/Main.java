package io.scriptor;

import io.scriptor.conf.*;
import io.scriptor.elf.Header;
import io.scriptor.elf.Identity;
import io.scriptor.elf.ProgramHeader;
import io.scriptor.elf.SectionHeader;
import io.scriptor.gdb.GDBServer;
import io.scriptor.impl.TrapException;
import io.scriptor.impl.device.CLINT;
import io.scriptor.impl.device.Memory;
import io.scriptor.impl.device.PLIC;
import io.scriptor.impl.device.UART;
import io.scriptor.isa.Registry;
import io.scriptor.machine.Device;
import io.scriptor.machine.Machine;
import io.scriptor.machine.MachineConfig;
import io.scriptor.util.ArgContext;
import io.scriptor.util.ChannelInputStream;
import io.scriptor.util.ExtendedInputStream;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static io.scriptor.elf.ELF.readSymbols;
import static io.scriptor.util.ByteUtil.readString;
import static io.scriptor.util.Resource.read;
import static java.util.function.Predicate.not;

public final class Main {

    // https://riscv.github.io/riscv-isa-manual/snapshot/privileged/
    // https://riscv.github.io/riscv-isa-manual/snapshot/unprivileged/
    // https://en.wikipedia.org/wiki/Executable_and_Linkable_Format
    // https://wiki.osdev.org/RISC-V_Bare_Bones

    public static void main(final @NotNull String @NotNull [] args) throws InterruptedException {

        final var logThread = new Thread(Log::handle, "rvm-log");
        logThread.start();

        try {
            final var context = new ArgContext();
            context.parse(args);

            final var machine = init(context);
            run(context, machine);
        } catch (final Exception e) {
            Log.error("%s", e);
        }

        Log.shutdown();
        logThread.join();
    }

    private static @NotNull Machine init(final @NotNull ArgContext args) {
        final var registry = Registry.getInstance();
        read(true, "index.list", stream -> {
            final var reader = new BufferedReader(new InputStreamReader(stream));
            reader.lines()
                  .map(String::trim)
                  .filter(not(String::isEmpty))
                  .filter(line -> line.endsWith(".isa"))
                  .forEach(name -> read(true, name, registry::parse));
        });

        final var resource     = !args.has("--config");
        final var resourceName = resource ? "config/default.conf" : args.get("--config");

        return read(resource, resourceName, stream -> {
            final var machineConfig = new MachineConfig();

            final var parser = new Parser(stream);
            final var root   = parser.parse();

            if (root.has("mode")) {
                final var mode = root.get(IntegerNode.class, "mode").value().intValue();
                machineConfig.mode(mode);
            }

            if (root.has("harts")) {
                final var harts = root.get(IntegerNode.class, "harts").value().intValue();
                machineConfig.harts(harts);
            }

            if (root.has("order")) {
                final var order = root.get(StringNode.class, "order").value();
                machineConfig.order(switch (order) {
                    case "le" -> ByteOrder.LITTLE_ENDIAN;
                    case "be" -> ByteOrder.BIG_ENDIAN;
                    default -> throw new NoSuchElementException(order);
                });
            }

            args.getAll("--load", val -> {
                final var parts    = val.split("=");
                final var filename = parts[0];
                final var offset = parts.length == 2
                                   ? Long.parseUnsignedLong(parts[1], 0x10)
                                   : 0L;
                final Function<Machine, Device> generator = m -> load(m, filename, offset);
                machineConfig.device(generator);
            });

            if (root.has("devices")) {
                final var devices = root.get(ArrayNode.class, "devices");
                for (final var entry : devices) {
                    final var node = entry.value;
                    final var type = node.get(StringNode.class, "type").value();

                    final Function<Machine, Device> generator = switch (type) {
                        case "clint" -> {
                            final var begin = node.get(IntegerNode.class, "begin").value();
                            yield m -> new CLINT(m, begin);
                        }
                        case "plic" -> {
                            final var begin = node.get(IntegerNode.class, "begin").value();
                            final var ndev  = node.get(IntegerNode.class, "ndev").value();
                            yield m -> new PLIC(m, begin, ndev.intValue());
                        }
                        case "uart" -> {
                            final var begin = node.get(IntegerNode.class, "begin").value();

                            final var inNode = node.get(ObjectNode.class, "in");
                            final var inType = inNode.get(StringNode.class, "type").value();

                            final boolean closeIn;
                            final InputStream in = switch (inType) {
                                case "system" -> {
                                    closeIn = false;

                                    yield System.in;
                                }
                                case "file" -> {
                                    closeIn = true;

                                    final var name = inNode.get(StringNode.class, "name").value();
                                    yield new FileInputStream(name);
                                }
                                default -> throw new NoSuchElementException("type=%s".formatted(inType));
                            };

                            final var outNode = node.get(ObjectNode.class, "out");
                            final var outType = outNode.get(StringNode.class, "type").value();

                            final boolean closeOut;
                            final OutputStream out = switch (outType) {
                                case "system" -> {
                                    closeOut = false;

                                    yield System.out;
                                }
                                case "file" -> {
                                    closeOut = true;

                                    final var name = outNode.get(StringNode.class, "name").value();
                                    final var append = outNode.has("append")
                                                       ? outNode.get(BooleanNode.class, "append").value()
                                                       : false;
                                    yield new FileOutputStream(name, append);
                                }
                                default -> throw new NoSuchElementException("type=%s".formatted(outType));
                            };

                            yield m -> new UART(m, begin, in, closeIn, out, closeOut);
                        }
                        case "memory" -> {
                            final var begin    = node.get(IntegerNode.class, "begin").value();
                            final var capacity = node.get(IntegerNode.class, "capacity").value();
                            final var readonly = node.has("readonly")
                                                 ? node.get(BooleanNode.class, "readonly").value()
                                                 : false;

                            yield m -> new Memory(m, begin, capacity.intValue(), readonly);
                        }
                        default -> throw new NoSuchElementException("type=%s".formatted(type));
                    };

                    machineConfig.device(generator);
                }
            }

            return machineConfig.configure();
        });
    }

    private static void run(final @NotNull ArgContext args, final @NotNull Machine machine) {
        machine.reset();

        final var debug = args.has("--debug");
        final var port  = args.getInt("--port", Integer::parseUnsignedInt, () -> 1234);

        if (debug) {
            try (final var gdb = new GDBServer(machine, port)) {
                while (gdb.step())
                    try {
                        machine.step();
                    } catch (final Exception e) {
                        machine.pause();
                        Log.inject(machine::dump);
                        Log.error("machine exception: %s", e);

                        if (e instanceof TrapException trap)
                            gdb.stop(trap.getId(), 0x05);
                    }
            } catch (final IOException e) {
                Log.error("failed to create gdb stub: %s", e);
            }
            return;
        }

        try {
            machine.spin();
            while (true)
                machine.step();
        } catch (final Exception e) {
            machine.pause();
            Log.inject(machine::dump);
            Log.error("machine exception: %s", e);
        }

        try {
            machine.close();
        } catch (final Exception e) {
            Log.error("closing machine: %s", e);
        }
    }

    private static @NotNull Device load(
            final @NotNull Machine machine,
            final @NotNull String filename,
            final long offset
    ) {
        try (final ExtendedInputStream stream = new ChannelInputStream(filename)) {
            final var buffer = ByteBuffer.allocateDirect(0x10);
            stream.read(buffer);
            final var identity = Identity.read(buffer.flip());

            final var elf = Arrays.compare(identity.magic(), new byte[] { 0x7F, 0x45, 0x4C, 0x46 }) == 0;

            if (elf) {
                final var header = Header.read(identity, stream);

                final var phtab = new ProgramHeader[header.phnum()];
                final var shtab = new SectionHeader[header.shnum()];

                for (int i = 0; i < header.phnum(); ++i) {
                    stream.seek(header.phoff() + i * header.phentsize());
                    phtab[i] = ProgramHeader.read(identity, stream);
                }

                for (int i = 0; i < header.shnum(); ++i) {
                    stream.seek(header.shoff() + i * header.shentsize());
                    shtab[i] = SectionHeader.read(identity, stream);
                }

                final var shstrtab = shtab[header.shstrndx()];

                SectionHeader symtab = null, dynsym = null, strtab = null, dynstr = null;
                for (final var sh : shtab) {
                    stream.seek(shstrtab.offset() + sh.name());
                    final var name = readString(stream);
                    switch (name) {
                        case ".symtab" -> symtab = sh;
                        case ".dynsym" -> dynsym = sh;
                        case ".strtab" -> strtab = sh;
                        case ".dynstr" -> dynstr = sh;
                    }
                }

                final var symbols = machine.symbols();
                if (symtab != null && strtab != null) {
                    readSymbols(identity, stream, symtab, strtab, symbols, offset);
                }
                if (dynsym != null && dynstr != null) {
                    readSymbols(identity, stream, dynsym, dynstr, symbols, offset);
                }

                long begin = -1L, end = 0L;

                for (final var ph : phtab) {
                    if (ph.type() != 0x01)
                        continue;

                    if (Long.compareUnsigned(begin, ph.paddr()) > 0)
                        begin = ph.paddr();
                    if (Long.compareUnsigned(end, ph.paddr() + ph.memsz()) < 0)
                        end = ph.paddr() + ph.memsz();
                }

                final var rom = new Memory(machine, begin + offset, (int) (end - begin), false);

                for (final var ph : phtab) {
                    if (ph.type() != 0x01)
                        continue;

                    stream.seek(ph.offset());

                    final var data = new byte[(int) ph.filesz()];
                    final var read = stream.read(data);
                    if (read != data.length)
                        Log.warn("stream read %d, requested %d", read, data.length);

                    rom.direct(data, (int) (ph.paddr() - begin), true);
                }

                return rom;
            } else {
                stream.seek(0L);

                final var rom = new Memory(machine, offset, (int) stream.size(), false);

                final var data = new byte[(int) stream.size()];
                final var read = stream.read(data);
                if (read != data.length)
                    Log.warn("stream read %d, requested %d", read, data.length);

                rom.direct(data, 0, true);
                return rom;
            }
        } catch (final IOException e) {
            Log.error("failed to load file '%s' (offset %x): %s", filename, offset, e);
            throw new RuntimeException(e);
        }
    }

    private Main() {
    }
}
