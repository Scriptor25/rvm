package io.scriptor.fdt

import java.nio.ByteBuffer

data class Node(val name: String, val props: Array<Prop>, val nodes: Array<Node>) {
    fun write(buffer: ByteBuffer, strings: StringTable) {
        buffer.putInt(Constant.FDT_BEGIN_NODE.toInt())

        buffer.put(name.toByteArray())

        do {
            buffer.put(0.toByte())
        } while ((buffer.position() and 3) != 0)

        for (prop in props) {
            prop.write(buffer, strings)
        }

        for (node in nodes) {
            node.write(buffer, strings)
        }

        buffer.putInt(Constant.FDT_END_NODE.toInt())
    }

    fun size(): Int {
        var size = 0
        size += Integer.SIZE
        size += name.toByteArray().size + 1
        size = (size + 3) and 3.inv()
        for (prop in props) {
            size += prop.size()
        }
        for (node in nodes) {
            size += node.size()
        }
        size += Integer.SIZE
        return size
    }
}
