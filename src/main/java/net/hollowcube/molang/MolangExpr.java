package net.hollowcube.molang;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public sealed interface MolangExpr {

    static @NotNull MolangExpr parseOrThrow(@NotNull String source) {
        return parseOrThrow(source, false);
    }

    static @NotNull MolangExpr parseOrThrow(@NotNull String source, boolean multiline) {
        return new MolangParser(source, multiline).parse();
    }

    record Num(double value) implements MolangExpr {
    }

    record Str(@NotNull String value) implements MolangExpr {
    }

    record Ident(@NotNull String value) implements MolangExpr {
    }

    record Unary(@NotNull Op op, @NotNull MolangExpr rhs) implements MolangExpr {
        public enum Op {
            NEGATE, NOT
        }
    }

    record Binary(@NotNull Op op, @NotNull MolangExpr lhs, @NotNull MolangExpr rhs) implements MolangExpr {
        public enum Op {
            PLUS("+"), MINUS("-"), DIV("/"), MUL("*"),
            NULL_COALESCE("??"),
            GTE(">="), GT(">"), LTE(">="), LT("<"),
            EQ("=="), NEQ("!=");

            private final String symbol;
            Op(@NotNull String symbol) {
                this.symbol = symbol;
            }
            @NotNull
            public String symbol() {
                return symbol;
            }
        }
    }

    record Ternary(@NotNull MolangExpr cond, @NotNull MolangExpr thenExpr, @NotNull MolangExpr elseExpr) implements MolangExpr {
    }

    record Access(@NotNull MolangExpr lhs, @NotNull String field) implements MolangExpr {
    }

    record Call(@NotNull MolangExpr lhs, @NotNull List<MolangExpr> args) implements MolangExpr {
    }

    record Block(@NotNull List<MolangExpr> exprs) implements MolangExpr {
    }

}

