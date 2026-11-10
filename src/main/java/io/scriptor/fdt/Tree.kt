package io.scriptor.fdt

import java.nio.ByteBuffer

data class Tree(val root: Node) {
    fun write(buffer: ByteBuffer, strings: StringTable) {
        root.write(buffer, strings)
        buffer.putInt(Constant.FDT_END.toInt())
    }

    fun size(): Int {
        var size = 0
        size += root.size()
        size += Integer.BYTES
        return size
    }
}

fun tree(block: NodeBuilder.() -> Unit): Tree {
    val root = NodeBuilder("").apply(block).build()
    return Tree(root)
}
