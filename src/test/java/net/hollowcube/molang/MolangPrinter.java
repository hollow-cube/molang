package net.hollowcube.molang;

import java.util.stream.Collectors;

final class MolangPrinter {

    public static String print(MolangExpr expr) {
        return switch (expr) {
            case MolangExpr.Num(var value, _) -> String.valueOf(value);
            case MolangExpr.Str(var value, _) -> "'" + value + "'";
            case MolangExpr.Ident(var value, _) -> value;
            case MolangExpr.Unary(
                var op,
                var rhs,
                _
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
                var elseExpr,
                _
            ) -> String.format("(? %s %s %s)", print(cond), print(thenExpr), print(elseExpr));
            case MolangExpr.Access(
                var lhs,
                var field,
                _
            ) -> String.format("(. %s %s)", print(lhs), field);
            case MolangExpr.Call(
                var lhs,
                var args,
                _
            ) -> String.format(
                "(? %s %s)", print(lhs),
                args.stream().map(MolangPrinter::print).collect(Collectors.joining(" "))
            );
            case MolangExpr.Assign(
                var target,
                var value,
                _
            ) -> String.format("(= %s %s)", print(target), print(value));
            case MolangExpr.Return(var value, _) -> String.format("(return %s)", print(value));
            case MolangExpr.Block(
                var exprs,
                _
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
            case PLUS -> "+";
            case NOT -> "!";
        };
    }

    private static String binaryOp(MolangExpr.Binary.Op op) {
        return op.symbol();
    }
}
