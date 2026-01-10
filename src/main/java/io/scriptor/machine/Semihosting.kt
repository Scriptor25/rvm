package io.scriptor.machine

interface Semihosting {

    companion object {
        fun fopen(machine: Machine, fname: String, mode: ULong, len: ULong): ULong {
            // TODO
            return 0UL.inv()
        }

        fun fread(machine: Machine, fd: ULong, memp: ULong, len: ULong): ULong {
            // TODO
            return 0UL
        }

        fun fwrite(machine: Machine, fd: ULong, memp: ULong, len: ULong): ULong {
            // TODO
            return 0UL
        }

        const val SEMIHOSTING_SYSOPEN = 0x01UL
        const val SEMIHOSTING_SYSWRITEC = 0x03UL
        const val SEMIHOSTING_SYSWRITE = 0x05UL
        const val SEMIHOSTING_SYSREAD = 0x06UL
        const val SEMIHOSTING_SYSREADC = 0x07UL
        const val SEMIHOSTING_SYSERRNO = 0x13UL

        const val SEMIHOSTING_SYSOPEN_MODE_READ = 0x0
        const val SEMIHOSTING_SYSOPEN_MODE_BINARY = 0x1
        const val SEMIHOSTING_SYSOPEN_MODE_PLUS = 0x2
        const val SEMIHOSTING_SYSOPEN_MODE_WRITE = 0x4
        const val SEMIHOSTING_SYSOPEN_MODE_APPEND = 0x8
    }
}
