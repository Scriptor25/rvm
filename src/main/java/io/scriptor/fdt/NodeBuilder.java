package io.scriptor.fdt;

import com.carrotsearch.hppc.ObjectArrayList;
import com.carrotsearch.hppc.ObjectIndexedContainer;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class NodeBuilder implements Buildable<Node> {

    public static @NotNull NodeBuilder create() {
        return new NodeBuilder();
    }

    private String name;
    private final ObjectIndexedContainer<Prop> props = new ObjectArrayList<>();
    private final ObjectIndexedContainer<Node> nodes = new ObjectArrayList<>();

    private NodeBuilder() {
    }

    public @NotNull NodeBuilder name(final @NotNull String name) {
        this.name = name;
        return this;
    }

    public @NotNull NodeBuilder prop(final @NotNull Prop prop) {
        this.props.add(prop);
        return this;
    }

    public @NotNull NodeBuilder prop(final @NotNull Consumer<PropBuilder> consumer) {
        final var builder = PropBuilder.create();
        consumer.accept(builder);
        this.props.add(builder.build());
        return this;
    }

    public @NotNull NodeBuilder node(final @NotNull Node node) {
        this.nodes.add(node);
        return this;
    }

    public @NotNull NodeBuilder node(final @NotNull Consumer<NodeBuilder> consumer) {
        final var builder = NodeBuilder.create();
        consumer.accept(builder);
        this.nodes.add(builder.build());
        return this;
    }

    @Override
    public @NotNull Node build() {
        if (name == null) {
            throw new IllegalStateException("missing node name");
        }

        return new Node(name, props.toArray(Prop.class), nodes.toArray(Node.class));
    }
}
