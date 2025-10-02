package io.scriptor.machine;

import org.jetbrains.annotations.NotNull;

public interface Semihosting {

    int SEMIHOSTING_SYSOPEN = 0x01;
    int SEMIHOSTING_SYSWRITEC = 0x03;
    int SEMIHOSTING_SYSWRITE = 0x05;
    int SEMIHOSTING_SYSREAD = 0x06;
    int SEMIHOSTING_SYSREADC = 0x07;
    int SEMIHOSTING_SYSERRNO = 0x13;

    int SEMIHOSTING_SYSOPEN_MODE_READ = 0x0;
    int SEMIHOSTING_SYSOPEN_MODE_BINARY = 0x1;
    int SEMIHOSTING_SYSOPEN_MODE_PLUS = 0x2;
    int SEMIHOSTING_SYSOPEN_MODE_WRITE = 0x4;
    int SEMIHOSTING_SYSOPEN_MODE_APPEND = 0x8;

    static long fopen(final @NotNull Machine machine, final @NotNull String fname, final long mode, final long len) {
        return -1;
    }

    static long fread(final @NotNull Machine machine, final long fd, final long memp, final long len) {
        return 0L;
    }

    static long fwrite(final @NotNull Machine machine, final long fd, final long memp, final long len) {
        return 0L;
    }
}
