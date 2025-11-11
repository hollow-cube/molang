package net.hollowcube.molang;

import java.util.List;
import java.util.stream.Collectors;

public sealed interface MolangExpr {

    static MolangExpr parseOrThrow(String source) {
        return parseOrThrow(source, false);
    }

    static MolangExpr parseOrThrow(String source, boolean multiline) {
        return new MolangParser(source, multiline).parse();
    }

    record Num(double value) implements MolangExpr {

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record Str(String value) implements MolangExpr {

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    record Ident(String value) implements MolangExpr {

        @Override
        public String toString() {
            return this.value;
        }
    }

    record Unary(Op op, MolangExpr rhs) implements MolangExpr {
        public enum Op {
            NEGATE, NOT
        }

        @Override
        public String toString() {
            return switch (op) {
                case NEGATE -> "-" + rhs;
                case NOT -> "!" + rhs;
            };
        }
    }

    record Binary(Op op, MolangExpr lhs, MolangExpr rhs) implements MolangExpr {
        public enum Op {
            PLUS("+"), MINUS("-"), DIV("/"), MUL("*"),
            NULL_COALESCE("??"),
            GTE(">="), GT(">"), LTE(">="), LT("<"),
            EQ("=="), NEQ("!="),
            AND("&&"), OR("||");

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

    record Ternary(MolangExpr cond, MolangExpr thenExpr,
                   MolangExpr elseExpr) implements MolangExpr {

        @Override
        public String toString() {
            return String.format("%s ? %s : %s", cond, thenExpr, elseExpr);
        }
    }

    record Access(MolangExpr lhs, String field) implements MolangExpr {

        @Override
        public String toString() {
            return lhs + "." + field;
        }
    }

    record ArrayAccess(MolangExpr lhs, MolangExpr index) implements MolangExpr {

        @Override
        public String toString() {
            return String.format("%s[%s]", lhs, index);
        }
    }

    record Call(MolangExpr lhs, List<MolangExpr> args) implements MolangExpr {

        @Override
        public String toString() {
            return lhs + "(" + args.stream().map(MolangExpr::toString).collect(Collectors.joining(", ")) + ")";
        }
    }

    record Block(List<MolangExpr> exprs) implements MolangExpr {

        @Override
        public String toString() {
            if (exprs.isEmpty()) {
                return "{}";
            }
            return "{" + exprs.stream().map(MolangExpr::toString).collect(Collectors.joining("; ")) + "}";
        }
    }

}

