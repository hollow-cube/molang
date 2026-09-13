package net.hollowcube.molang.eval;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        /// A holder of the given entries. Names are case-insensitive, like all Molang names, so
        /// entries whose names only differ by case are rejected.
        ///
        /// @throws IllegalArgumentException if two names only differ by case
        static Holder holder(Map<String, MolangValue> map) {
            return new MolangEvaluator.HolderImpl(normalizeNames(map));
        }

        /// The form of a name holders are keyed by: Molang names are case-insensitive, so lowercase.
        static String normalizeName(String name) {
            return name.toLowerCase(Locale.ROOT);
        }

        /// Copies entries with their names normalized by [#normalizeName(String)].
        ///
        /// @throws IllegalArgumentException if two names only differ by case
        static Map<String, MolangValue> normalizeNames(Map<String, MolangValue> map) {
            var normalized = new HashMap<String, MolangValue>();
            for (var entry : map.entrySet()) {
                if (normalized.put(normalizeName(entry.getKey()), entry.getValue()) != null)
                    throw new IllegalArgumentException(
                        "duplicate name (names are case-insensitive): " + entry.getKey()
                    );
            }
            return Map.copyOf(normalized);
        }

        /// Returns the value of a field, or [#NIL] if there is none.
        ///
        /// Names from parsed Molang are already lowercase. Implementations should treat field
        /// case-insensitively, eg by comparing against [#normalizeName(String)].
        MolangValue get(String field);

        interface Mutable extends Holder {
            void set(String field, MolangValue value);
        }
    }

}
