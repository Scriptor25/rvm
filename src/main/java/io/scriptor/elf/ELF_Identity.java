package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

public final class ELF_Identity {

    /**
     * 0x7F followed by ELF (45 4c 46) in ASCII;
     * these four bytes constitute the magic number.
     */
    public final byte[] magic = new byte[4];
    /**
     * This byte is set to either 1 or 2 to signify 32- or 64-bit format, respectively.
     * <br>
     * This byte is set to either 1 or 2 to signify little or big endianness, respectively.
     * This affects interpretation of multi-byte fields starting with offset 0x10.
     */
    public final ELF_Format format;
    /**
     * Set to 1 for the original and current version of ELF.
     */
    public final byte version;
    /**
     * Identifies the target operating system ABI.
     */
    public final ELF_OSABI osabi;
    /**
     * Further specifies the ABI version.
     */
    public final byte abiVersion;

    public ELF_Identity(final byte @NotNull [] identity) {
        System.arraycopy(identity, 0, magic, 0, 4);
        format = ELF_Format.of(identity[0x04], identity[0x05]);
        version = identity[0x06];
        osabi = ELF_OSABI.of(identity[0x07]);
        abiVersion = identity[0x08];
    }
}
