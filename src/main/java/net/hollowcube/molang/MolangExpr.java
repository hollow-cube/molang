package net.hollowcube.molang;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public sealed interface MolangExpr {

    static @NotNull MolangExpr parseOrThrow(@NotNull String source) {
        return parseOrThrow(source, false);
    }

    static @NotNull MolangExpr parseOrThrow(@NotNull String source, boolean multiline) {
        return new MolangParser(source, multiline).parse();
    }

    record Num(double value) implements MolangExpr {

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record Str(@NotNull String value) implements MolangExpr {

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    record Ident(@NotNull String value) implements MolangExpr {

        @Override
        public String toString() {
            return this.value;
        }
    }

    record Unary(@NotNull Op op, @NotNull MolangExpr rhs) implements MolangExpr {
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

    record Binary(@NotNull Op op, @NotNull MolangExpr lhs, @NotNull MolangExpr rhs) implements MolangExpr {
        public enum Op {
            PLUS("+"), MINUS("-"), DIV("/"), MUL("*"),
            NULL_COALESCE("??"),
            GTE(">="), GT(">"), LTE(">="), LT("<"),
            EQ("=="), NEQ("!="),
            AND("&&"), OR("||");

            private final String symbol;

            Op(@NotNull String symbol) {
                this.symbol = symbol;
            }

            @NotNull
            public String symbol() {
                return symbol;
            }
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", lhs, op.symbol(), rhs);
        }
    }

    record Ternary(@NotNull MolangExpr cond, @NotNull MolangExpr thenExpr,
                   @NotNull MolangExpr elseExpr) implements MolangExpr {

        @Override
        public String toString() {
            return String.format("%s ? %s : %s", cond, thenExpr, elseExpr);
        }
    }

    record Access(@NotNull MolangExpr lhs, @NotNull String field) implements MolangExpr {

        @Override
        public String toString() {
            return lhs + "." + field;
        }
    }

    record Call(@NotNull MolangExpr lhs, @NotNull List<MolangExpr> args) implements MolangExpr {

        @Override
        public String toString() {
            return lhs + "(" + args.stream().map(MolangExpr::toString).collect(Collectors.joining(", ")) + ")";
        }
    }

    record Block(@NotNull List<MolangExpr> exprs) implements MolangExpr {

        @Override
        public String toString() {
            if (exprs.isEmpty()) {
                return "{}";
            }
            return "{" + exprs.stream().map(MolangExpr::toString).collect(Collectors.joining("; ")) + "}";
        }
    }

}

