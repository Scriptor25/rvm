package io.scriptor.fdt

class NodeBuilder(var name: String? = null) : Buildable<Node> {

    private val props = mutableListOf<Prop>()
    private val nodes = mutableListOf<Node>()

    fun name(str: String) {
        name = str
    }

    fun prop(prop: Prop) {
        props.add(prop)
    }

    fun prop(name: String, block: PropBuilder.() -> Unit) {
        props.add(PropBuilder(name).apply(block).build())
    }

    fun prop(condition: Boolean, name: String, block: PropBuilder.() -> Unit) {
        if (!condition) return

        props.add(PropBuilder(name).apply(block).build())
    }

    fun node(node: Node) {
        nodes.add(node)
    }

    fun node(block: NodeBuilder.() -> Unit) {
        nodes.add(NodeBuilder().apply(block).build())
    }

    fun node(name: String, block: NodeBuilder.() -> Unit) {
        nodes.add(NodeBuilder(name).apply(block).build())
    }

    override fun build(): Node {
        return Node(requireNotNull(name), props.toTypedArray(), nodes.toTypedArray())
    }
}
