package net.hollowcube.molang;

/// A value an expression can produce, store in a variable or pass to a function: a number, a string,
/// or null for something that does not exist (eg an unset variable), which `??` (null coalescing)
/// replaces.
public sealed interface MolangValue {
    MolangValue NULL = new Null();

    record Null() implements MolangValue {
        @Override
        public String toString() {
            return "null";
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
            return "'" + value + "'";
        }
    }
}
