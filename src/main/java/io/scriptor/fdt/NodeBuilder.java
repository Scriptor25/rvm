package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class NodeBuilder implements Buildable<Node> {

    public static @NotNull NodeBuilder create() {
        return new NodeBuilder();
    }

    private String name;
    private final List<Prop> props = new ArrayList<>();
    private final List<Node> nodes = new ArrayList<>();

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

        return new Node(name, props.toArray(Prop[]::new), nodes.toArray(Node[]::new));
    }
}
