package io.scriptor.elf;

public interface ELF {

    byte CLASS_32 = 0x1;
    byte CLASS_64 = 0x2;

    byte DATA_LE = 0x1;
    byte DATA_BE = 0x2;

    byte VERSION_1 = 0x1;

    byte OSABI_SYSTEM_V = 0x00;
    byte OSABI_HP_UX = 0x01;
    byte OSABI_NET_BSD = 0x02;
    byte OSABI_LINUX = 0x03;
    byte OSABI_GNU_HURD = 0x04;
    byte OSABI_SOLARIS = 0x06;
    byte OSABI_AIX = 0x07;
    byte OSABI_IRIX = 0x08;
    byte OSABI_FREE_BSD = 0x09;
    byte OSABI_TRU64 = 0x0a;
    byte OSABI_NOVELL_MODESTO = 0x0b;
    byte OSABI_OPEN_BSD = 0x0c;
    byte OSABI_OPEN_VMS = 0x0d;
    byte OSABI_NON_STOP_KERNEL = 0x0e;
    byte OSABI_AROS = 0x0f;
    byte OSABI_FENIX_OS = 0x10;
    byte OSABI_NUXI_CLOUD_ABI = 0x11;
    byte OSABI_STRATUS_TECHNOLOGIES_OPEN_VOS = 0x12;

    short TYPE_NONE = 0x0000;
    short TYPE_REL = 0x0001;
    short TYPE_EXEC = 0x0002;
    short TYPE_DYN = 0x0003;
    short TYPE_CORE = 0x0004;

    short MACHINE_RISC_V = 0x00F3;
}
