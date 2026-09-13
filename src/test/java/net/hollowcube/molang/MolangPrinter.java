package net.hollowcube.molang;

import java.util.stream.Collectors;

final class MolangPrinter {

    public static String print(MolangExpr expr) {
        return switch (expr) {
            case MolangExpr.Num(var value) -> String.valueOf(value);
            case MolangExpr.Str(var value) -> "'" + value + "'";
            case MolangExpr.Ident(var value) -> value;
            case MolangExpr.Unary(
                var op,
                var rhs
            ) -> String.format("(%s %s)", unaryOp(op), print(rhs));
            case MolangExpr.Binary binary -> String.format(
                "(%s %s %s)",
                binaryOp(binary.op()),
                print(binary.lhs()),
                print(binary.rhs())
            );
            case MolangExpr.Ternary(
                var cond,
                var thenExpr,
                var elseExpr
            ) -> String.format("(? %s %s %s)", print(cond), print(thenExpr), print(elseExpr));
            case MolangExpr.Access(
                var lhs,
                var field
            ) -> String.format("(. %s %s)", print(lhs), field);
            case MolangExpr.Call(
                var lhs,
                var args
            ) -> String.format(
                "(? %s %s)", print(lhs),
                args.stream().map(MolangPrinter::print).collect(Collectors.joining(" "))
            );
            case MolangExpr.Assign(
                var target,
                var value
            ) -> String.format("(= %s %s)", print(target), print(value));
            case MolangExpr.Block(
                var exprs
            ) -> exprs.isEmpty()
                ? "{ }"
                : String.format(
                    "{ %s }",
                    exprs.stream().map(MolangPrinter::print).collect(Collectors.joining("; "))
                );
        };
    }

    private static String unaryOp(MolangExpr.Unary.Op op) {
        return switch (op) {
            case NEGATE -> "-";
            case NOT -> "!";
        };
    }

    private static String binaryOp(MolangExpr.Binary.Op op) {
        return op.symbol();
    }
}
