package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

public final class HeaderBuilder implements Buildable<Header> {

    private int magic;
    private int totalsize;
    private int off_dt_struct;
    private int off_dt_strings;
    private int off_mem_rsvmap;
    private int version;
    private int last_comp_version;
    private int boot_cpuid_phys;
    private int size_dt_strings;
    private int size_dt_struct;

    public @NotNull HeaderBuilder magic(final int magic) {
        this.magic = magic;
        return this;
    }

    public @NotNull HeaderBuilder totalsize(final int totalsize) {
        this.totalsize = totalsize;
        return this;
    }

    public @NotNull HeaderBuilder off_dt_struct(final int off_dt_struct) {
        this.off_dt_struct = off_dt_struct;
        return this;
    }

    public @NotNull HeaderBuilder off_dt_strings(final int off_dt_strings) {
        this.off_dt_strings = off_dt_strings;
        return this;
    }

    public @NotNull HeaderBuilder off_mem_rsvmap(final int off_mem_rsvmap) {
        this.off_mem_rsvmap = off_mem_rsvmap;
        return this;
    }

    public @NotNull HeaderBuilder version(final int version) {
        this.version = version;
        return this;
    }

    public @NotNull HeaderBuilder last_comp_version(final int last_comp_version) {
        this.last_comp_version = last_comp_version;
        return this;
    }

    public @NotNull HeaderBuilder boot_cpuid_phys(final int boot_cpuid_phys) {
        this.boot_cpuid_phys = boot_cpuid_phys;
        return this;
    }

    public @NotNull HeaderBuilder size_dt_strings(final int size_dt_strings) {
        this.size_dt_strings = size_dt_strings;
        return this;
    }

    public @NotNull HeaderBuilder size_dt_struct(final int size_dt_struct) {
        this.size_dt_struct = size_dt_struct;
        return this;
    }

    @Override
    public @NotNull Header build() {
        return new Header(magic,
                          totalsize,
                          off_dt_struct,
                          off_dt_strings,
                          off_mem_rsvmap,
                          version,
                          last_comp_version,
                          boot_cpuid_phys,
                          size_dt_strings,
                          size_dt_struct);
    }
}
