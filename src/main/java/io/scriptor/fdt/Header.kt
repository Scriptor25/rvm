package io.scriptor.fdt

import java.nio.ByteBuffer

/**
 * The layout of the header for the devicetree is defined by the following C structure. All the header fields are
 * 32-bit integers, stored in big-endian format.
 * 
 * @param magic             This field shall contain the value 0xd00dfeed (big-endian).
 * @param totalsize         This field shall contain the total size in bytes of the devicetree data structure.
 * This size shall encompass all sections of the structure: the header, the memory reservation
 * block, structure block and strings block, as well as any free space gaps between the blocks
 * or after the final block.
 * @param off_dt_struct     This field shall contain the offset in bytes of the structure block from the beginning of
 * the header.
 * @param off_dt_strings    This field shall contain the offset in bytes of the strings block from the beginning of the
 * header.
 * @param off_mem_rsvmap    This field shall contain the offset in bytes of the memory reservation block from the
 * beginning of the header.
 * @param version           This field shall contain the version of the devicetree data structure. The version is 17 if
 * using the structure as defined in this document. An DTSpec boot program may provide the
 * devicetree of a later version, in which case this field shall contain the version number
 * defined in whichever later document gives the details of that version.
 * @param last_comp_version This field shall contain the lowest version of the devicetree data structure with which the
 * version used is backwards compatible. So, for the structure as defined in this document
 * (version 17), this field shall contain 16 because version 17 is backwards compatible with
 * version 16, but not earlier versions. As per Section 5.1, a DTSpec boot program should
 * provide a devicetree in a format which is backwards compatible with version 16, and thus
 * this field shall always contain 16.
 * @param boot_cpuid_phys   This field shall contain the physical ID of the system’s boot CPU. It shall be identical to
 * the physical ID given in the reg property of that CPU node within the devicetree.
 * @param size_dt_strings   This field shall contain the length in bytes of the strings block section of the devicetree
 * blob.
 * @param size_dt_struct    This field shall contain the length in bytes of the structure block section of the
 * devicetree blob.
 */
data class Header(
    val magic: UInt,
    val totalsize: UInt,
    val off_dt_struct: UInt,
    val off_dt_strings: UInt,
    val off_mem_rsvmap: UInt,
    val version: UInt,
    val last_comp_version: UInt,
    val boot_cpuid_phys: UInt,
    val size_dt_strings: UInt,
    val size_dt_struct: UInt,
) {
    fun write(buffer: ByteBuffer) {
        buffer
            .putInt(magic.toInt())
            .putInt(totalsize.toInt())
            .putInt(off_dt_struct.toInt())
            .putInt(off_dt_strings.toInt())
            .putInt(off_mem_rsvmap.toInt())
            .putInt(version.toInt())
            .putInt(last_comp_version.toInt())
            .putInt(boot_cpuid_phys.toInt())
            .putInt(size_dt_strings.toInt())
            .putInt(size_dt_struct.toInt())
    }

    companion object {
        const val BYTES = Int.SIZE_BYTES * 10
    }
}
