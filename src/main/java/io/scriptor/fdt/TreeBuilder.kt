package io.scriptor.fdt

import java.util.function.Consumer

class TreeBuilder private constructor() : Buildable<Tree> {
    private var root: Node? = null

    fun root(root: Node): TreeBuilder {
        this.root = root
        return this
    }

    fun root(consumer: Consumer<NodeBuilder>): TreeBuilder {
        val builder: NodeBuilder = NodeBuilder.create()
        consumer.accept(builder)
        this.root = builder.build()
        return this
    }

    override fun build(): Tree {
        checkNotNull(root) { "missing tree root" }

        return Tree(root!!)
    }

    companion object {
        fun create(): TreeBuilder {
            return TreeBuilder()
        }
    }
}
