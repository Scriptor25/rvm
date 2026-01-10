package io.scriptor.fdt

class HeaderBuilder : Buildable<Header> {

    private var magic = 0u
    private var totalsize = 0u
    private var off_dt_struct = 0u
    private var off_dt_strings = 0u
    private var off_mem_rsvmap = 0u
    private var version = 0u
    private var last_comp_version = 0u
    private var boot_cpuid_phys = 0u
    private var size_dt_strings = 0u
    private var size_dt_struct = 0u

    fun magic(magic: UInt): HeaderBuilder {
        this.magic = magic
        return this
    }

    fun totalsize(totalsize: UInt): HeaderBuilder {
        this.totalsize = totalsize
        return this
    }

    fun off_dt_struct(off_dt_struct: UInt): HeaderBuilder {
        this.off_dt_struct = off_dt_struct
        return this
    }

    fun off_dt_strings(off_dt_strings: UInt): HeaderBuilder {
        this.off_dt_strings = off_dt_strings
        return this
    }

    fun off_mem_rsvmap(off_mem_rsvmap: UInt): HeaderBuilder {
        this.off_mem_rsvmap = off_mem_rsvmap
        return this
    }

    fun version(version: UInt): HeaderBuilder {
        this.version = version
        return this
    }

    fun last_comp_version(last_comp_version: UInt): HeaderBuilder {
        this.last_comp_version = last_comp_version
        return this
    }

    fun boot_cpuid_phys(boot_cpuid_phys: UInt): HeaderBuilder {
        this.boot_cpuid_phys = boot_cpuid_phys
        return this
    }

    fun size_dt_strings(size_dt_strings: UInt): HeaderBuilder {
        this.size_dt_strings = size_dt_strings
        return this
    }

    fun size_dt_struct(size_dt_struct: UInt): HeaderBuilder {
        this.size_dt_struct = size_dt_struct
        return this
    }

    override fun build(): Header {
        return Header(
            magic,
            totalsize,
            off_dt_struct,
            off_dt_strings,
            off_mem_rsvmap,
            version,
            last_comp_version,
            boot_cpuid_phys,
            size_dt_strings,
            size_dt_struct,
        )
    }
}
