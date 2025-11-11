package net.hollowcube.molang.eval;

import java.util.List;
import java.util.Map;

public sealed interface MolangValue {
    MolangValue NIL = new Nil();

    record Nil() implements MolangValue {
        @Override
        public String toString() {
            return "undefined";
        }
    }

    record Num(double value) implements MolangValue {
        public Num(boolean value) {
            this(value ? 1.0 : 0.0);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record Str(String value) implements MolangValue {
        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    @FunctionalInterface
    non-sealed interface Function extends MolangValue {

        MolangValue apply(List<MolangValue> args);

    }

    non-sealed interface Holder extends MolangValue {
        static Holder holder(Map<String, MolangValue> map) {
            return new MolangEvaluator.HolderImpl(Map.copyOf(map));
        }

        MolangValue get(String field);

        interface Mutable extends Holder {
            void set(String field, MolangValue value);
        }
    }

    non-sealed interface Array extends MolangValue {

        int size();

        MolangValue get(int index);

        interface Mutable extends Array {
            void set(int index, MolangValue value);
        }
    }

}
