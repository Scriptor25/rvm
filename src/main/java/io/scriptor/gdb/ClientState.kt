package io.scriptor.gdb

import java.nio.ByteBuffer

class ClientState {

    val buffer: ByteBuffer = ByteBuffer.allocateDirect(0x2000)
    val packet: StringBuilder = StringBuilder()
    val packetChecksum: StringBuilder = StringBuilder()

    var append: Boolean = false
    var end: Boolean = false

    var checksum: UInt = 0u

    var noack: Boolean = false

    var stopId: Int = -1
    var stopCode: UInt = 0x00u
}
