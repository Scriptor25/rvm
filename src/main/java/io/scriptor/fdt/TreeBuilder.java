package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class TreeBuilder implements Buildable<Tree> {

    public static @NotNull TreeBuilder create() {
        return new TreeBuilder();
    }

    private Node root;

    private TreeBuilder() {
    }

    public @NotNull TreeBuilder root(final @NotNull Node root) {
        this.root = root;
        return this;
    }

    public @NotNull TreeBuilder root(final @NotNull Consumer<NodeBuilder> consumer) {
        final var builder = NodeBuilder.create();
        consumer.accept(builder);
        this.root = builder.build();
        return this;
    }

    @Override
    public @NotNull Tree build() {
        if (root == null) {
            throw new IllegalStateException("missing tree root");
        }

        return new Tree(root);
    }
}
