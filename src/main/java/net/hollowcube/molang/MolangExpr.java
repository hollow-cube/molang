package net.hollowcube.molang;

import java.util.List;
import java.util.stream.Collectors;

/// A parsed Molang expression. Internal: hosts compile source with [MolangEnvironment#compile(String)].
///
/// Every expression has a `position`: the index in its source it is reported at, eg its first
/// character, or the operator of a binary expression (`/` for a division by zero). It is -1 for an
/// expression that was not parsed from source.
sealed interface MolangExpr {

    int position();

    /// Molang names are case-insensitive, so names are compared in this form: lowercase.
    static String normalizeName(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    record Num(double value, int position) implements MolangExpr {
        public Num(double value) {
            this(value, -1);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record Str(String value, int position) implements MolangExpr {
        public Str(String value) {
            this(value, -1);
        }

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    record Ident(String value, int position) implements MolangExpr {
        public Ident(String value) {
            this(value, -1);
        }

        @Override
        public String toString() {
            return this.value;
        }
    }

    record Unary(Op op, MolangExpr rhs, int position) implements MolangExpr {
        public Unary(Op op, MolangExpr rhs) {
            this(op, rhs, -1);
        }

        public enum Op {
            NEGATE,
            PLUS,
            NOT,
        }

        @Override
        public String toString() {
            return switch (op) {
                case NEGATE -> "-" + rhs;
                case PLUS -> "+" + rhs;
                case NOT -> "!" + rhs;
            };
        }
    }

    record Binary(Op op, MolangExpr lhs, MolangExpr rhs, int position) implements MolangExpr {
        public Binary(Op op, MolangExpr lhs, MolangExpr rhs) {
            this(op, lhs, rhs, -1);
        }

        public enum Op {
            PLUS("+"),
            MINUS("-"),
            DIV("/"),
            MUL("*"),
            NULL_COALESCE("??"),
            GTE(">="),
            GT(">"),
            LTE("<="),
            LT("<"),
            EQ("=="),
            NEQ("!="),
            AND("&&"),
            OR("||"),
            ;

            private final String symbol;

            Op(String symbol) {
                this.symbol = symbol;
            }

            public String symbol() {
                return symbol;
            }
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", lhs, op.symbol(), rhs);
        }
    }

    record Ternary(
        MolangExpr cond,
        MolangExpr thenExpr,
        MolangExpr elseExpr,
        int position
    ) implements MolangExpr {
        public Ternary(MolangExpr cond, MolangExpr thenExpr, MolangExpr elseExpr) {
            this(cond, thenExpr, elseExpr, -1);
        }

        @Override
        public String toString() {
            return String.format("%s ? %s : %s", cond, thenExpr, elseExpr);
        }
    }

    record Access(MolangExpr lhs, String field, int position) implements MolangExpr {
        public Access(MolangExpr lhs, String field) {
            this(lhs, field, -1);
        }

        @Override
        public String toString() {
            return lhs + "." + field;
        }
    }

    /// Assignment to a field of a writable holder (eg `v.x = 1`), which evaluates to 0.
    record Assign(Access target, MolangExpr value, int position) implements MolangExpr {
        public Assign(Access target, MolangExpr value) {
            this(target, value, -1);
        }

        @Override
        public String toString() {
            return target + " = " + value;
        }
    }

    /// `return value`, which stops evaluation of the whole expression and yields value.
    record Return(MolangExpr value, int position) implements MolangExpr {
        public Return(MolangExpr value) {
            this(value, -1);
        }

        @Override
        public String toString() {
            return "return " + value;
        }
    }

    record Call(MolangExpr lhs, List<MolangExpr> args, int position) implements MolangExpr {
        public Call(MolangExpr lhs, List<MolangExpr> args) {
            this(lhs, args, -1);
        }

        @Override
        public String toString() {
            return lhs
                + "("
                + args.stream().map(MolangExpr::toString).collect(Collectors.joining(", "))
                + ")";
        }
    }

    record Block(List<MolangExpr> exprs, int position) implements MolangExpr {
        public Block(List<MolangExpr> exprs) {
            this(exprs, -1);
        }

        @Override
        public String toString() {
            if (exprs.isEmpty()) {
                return "{}";
            }
            return "{"
                + exprs.stream().map(MolangExpr::toString).collect(Collectors.joining("; "))
                + "}";
        }
    }

}
