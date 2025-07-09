package net.hollowcube.molang.eval;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public sealed interface MolangValue {
    @NotNull MolangValue NIL = new Nil();

    record Nil() implements MolangValue {
        @Override
        public @NotNull String toString() {
            return "undefined";
        }
    }

    record Num(double value) implements MolangValue {
        public Num(boolean value) {
            this(value ? 1.0 : 0.0);
        }

        @Override
        public @NotNull String toString() {
            return String.valueOf(value);
        }
    }

    record Str(String value) implements MolangValue {
        @Override
        public @NotNull String toString() {
            return "\"" + value + "\"";
        }
    }

    @FunctionalInterface
    non-sealed interface Function extends MolangValue {

        @NotNull MolangValue apply(@NotNull List<MolangValue> args);

    }

}
