package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public final class SymbolTable implements Iterable<Symbol> {

    private static final String NULL = "(null)";

    private final TreeMap<Long, String> map = new TreeMap<>();

    public @NotNull String resolve(final long addr) {
        if (map.isEmpty()) {
            return NULL;
        }

        final var entry = map.floorEntry(addr);
        if (entry == null) {
            return NULL;
        }

        if (entry.getKey() == addr) {
            return entry.getValue();
        }

        return "%s+0x%x".formatted(entry.getValue(), addr - entry.getKey());
    }

    public void put(final long addr, final @NotNull String name) {
        map.put(addr, name);
    }

    public long get(final @NotNull String name) {
        return map.entrySet()
                  .stream()
                  .filter(entry -> entry.getValue().equals(name))
                  .map(Map.Entry::getKey)
                  .mapToLong(Long::longValue)
                  .findAny()
                  .orElse(~0L);
    }

    @Override
    public @NotNull Iterator<Symbol> iterator() {
        return map.entrySet()
                  .stream()
                  .map(entry -> new Symbol(entry.getKey(), entry.getValue()))
                  .iterator();
    }
}
