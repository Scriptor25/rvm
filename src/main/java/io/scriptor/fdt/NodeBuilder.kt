package io.scriptor.fdt

import java.util.function.Consumer

class NodeBuilder : Buildable<Node> {

    private var name: String? = null
    private val props: MutableList<Prop> = ArrayList()
    private val nodes: MutableList<Node> = ArrayList()

    fun name(name: String): NodeBuilder {
        this.name = name
        return this
    }

    fun prop(prop: Prop): NodeBuilder {
        this.props.add(prop)
        return this
    }

    fun prop(consumer: Consumer<PropBuilder>): NodeBuilder {
        val builder = PropBuilder()
        consumer.accept(builder)
        this.props.add(builder.build())
        return this
    }

    fun node(node: Node): NodeBuilder {
        this.nodes.add(node)
        return this
    }

    fun node(consumer: Consumer<NodeBuilder>): NodeBuilder {
        val builder = NodeBuilder()
        consumer.accept(builder)
        this.nodes.add(builder.build())
        return this
    }

    override fun build(): Node {
        checkNotNull(name) { "missing node name" }

        return Node(name!!, props.toTypedArray(), nodes.toTypedArray())
    }
}
