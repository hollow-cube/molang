package net.hollowcube.molang;

import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

final class MolangPrinter {

    public static @NotNull String print(@NotNull MolangExpr expr) {
        return switch (expr) {
            case MolangExpr.Num(var value) -> String.valueOf(value);
            case MolangExpr.Str(var value) -> "'" + value + "'";
            case MolangExpr.Ident(var value) -> value;
            case MolangExpr.Unary(var op, var rhs) -> String.format("(%s %s)", unaryOp(op), print(rhs));
            case MolangExpr.Binary binary -> String.format("(%s %s %s)",
                    binaryOp(binary.op()), print(binary.lhs()), print(binary.rhs()));
            case MolangExpr.Ternary(var cond, var thenExpr, var elseExpr) -> String.format("(? %s %s %s)",
                    print(cond), print(thenExpr), print(elseExpr));
            case MolangExpr.Access(var lhs, var field) -> String.format("(. %s %s)", print(lhs), field);
            case MolangExpr.ArrayAccess(var lhs, var index) -> String.format("([ %s %s)", print(lhs), print(index));
            case MolangExpr.Call(var lhs, var args) -> String.format("(? %s %s)", print(lhs),
                    args.stream().map(MolangPrinter::print).collect(Collectors.joining(" ")));
            case MolangExpr.Block(var exprs) -> exprs.isEmpty() ? "{ }" : String.format("{ %s }", exprs.stream()
                    .map(MolangPrinter::print).collect(Collectors.joining("; ")));
        };
    }

    private static @NotNull String unaryOp(@NotNull MolangExpr.Unary.Op op) {
        return switch (op) {
            case NEGATE -> "-";
            case NOT -> "!";
        };
    }

    private static @NotNull String binaryOp(@NotNull MolangExpr.Binary.Op op) {
        return op.symbol();
    }
}
