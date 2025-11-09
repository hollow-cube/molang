package net.hollowcube.molang;

import net.hollowcube.molang.eval.MolangValue;
import net.hollowcube.molang.runtime.MolangMath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Implements some (very basic) optimizations for a Molang AST.
public final class MolangOptimizer {

    public static @NotNull MolangExpr optimizeAst(@NotNull MolangExpr expr) {
        return switch (expr) {
            case MolangExpr.Num num -> num;
            case MolangExpr.Str str -> str;
            case MolangExpr.Ident ident -> ident;
            case MolangExpr.Unary unary -> {
                final MolangExpr rhs = optimizeAst(unary.rhs());
                if (rhs instanceof MolangExpr.Num(double value)) {
                    yield new MolangExpr.Num(switch (unary.op()) {
                        case NEGATE -> -value;
                        case NOT -> value == 0 ? 1.0 : 0.0;
                    });
                }
                yield new MolangExpr.Unary(unary.op(), rhs);
            }
            case MolangExpr.Binary binary -> {
                final MolangExpr lhs = optimizeAst(binary.lhs());
                final MolangExpr rhs = optimizeAst(binary.rhs());
                if (lhs instanceof MolangExpr.Num(double leftValue) && rhs instanceof MolangExpr.Num(
                        double rightValue
                )) {
                    yield new MolangExpr.Num(switch (binary.op()) {
                        case PLUS -> leftValue + rightValue;
                        case MINUS -> leftValue - rightValue;
                        case MUL -> leftValue * rightValue;
                        case DIV -> rightValue != 0 ? leftValue / rightValue : Double.NaN; // Avoid division by zero
                        case NULL_COALESCE -> leftValue != 0 ? leftValue : rightValue;
                        case GTE -> leftValue >= rightValue ? 1.0 : 0.0;
                        case GT -> leftValue > rightValue ? 1.0 : 0.0;
                        case LTE -> leftValue <= rightValue ? 1.0 : 0.0;
                        case LT -> leftValue < rightValue ? 1.0 : 0.0;
                        case EQ -> leftValue == rightValue ? 1.0 : 0.0;
                        case NEQ -> leftValue != rightValue ? 1.0 : 0.0;
                        case AND -> leftValue != 0 && rightValue != 0 ? 1.0 : 0.0;
                        case OR -> leftValue != 0 || rightValue != 0 ? 1.0 : 0.0;
                    });
                }
                if (lhs instanceof MolangExpr.Str(String leftValue) && rhs instanceof MolangExpr.Str(
                        String rightValue
                )) {
                    if (binary.op() == MolangExpr.Binary.Op.EQ)
                        yield new MolangExpr.Num(leftValue.equals(rightValue) ? 1.0 : 0.0);
                    if (binary.op() == MolangExpr.Binary.Op.NEQ)
                        yield new MolangExpr.Num(!leftValue.equals(rightValue) ? 1.0 : 0.0);
                }
                yield new MolangExpr.Binary(binary.op(), lhs, rhs);
            }
            case MolangExpr.Ternary ternary -> {
                final MolangExpr cond = optimizeAst(ternary.cond());
                final MolangExpr thenExpr = optimizeAst(ternary.thenExpr());
                final MolangExpr elseExpr = optimizeAst(ternary.elseExpr());
                if (cond instanceof MolangExpr.Num(double value))
                    yield value != 0 ? thenExpr : elseExpr;
                yield new MolangExpr.Ternary(cond, thenExpr, elseExpr);
            }
            case MolangExpr.Access access -> {
                final MolangExpr lhs = optimizeAst(access.lhs());
                // Math functions can be optimized specifically
                if (lhs instanceof MolangExpr.Ident(var ident) && ("math".equals(ident) || "m".equals(ident))) {
                    final MolangExpr result = optimizeMathCall(access.field(), List.of());
                    if (result != null) yield result;
                }
                yield new MolangExpr.Access(lhs, access.field());
            }
            case MolangExpr.Call call -> {
                final MolangExpr lhs = optimizeAst(call.lhs());
                final List<MolangExpr> args = new ArrayList<>();
                for (MolangExpr arg : call.args()) args.add(optimizeAst(arg));
                // Math functions can be optimized specifically
                if (lhs instanceof MolangExpr.Access(var accessLhs, var field) &&
                        accessLhs instanceof MolangExpr.Ident(var ident) &&
                        ("math".equals(ident) || "m".equals(ident))) {
                    final MolangExpr result = optimizeMathCall(field, args);
                    if (result != null) yield result;
                }
                yield new MolangExpr.Call(lhs, List.copyOf(args));
            }
            case MolangExpr.Block block -> {
                var optimizedExprs = new ArrayList<MolangExpr>();
                for (MolangExpr subExpr : block.exprs())
                    optimizedExprs.add(optimizeAst(subExpr));
                yield new MolangExpr.Block(List.copyOf(optimizedExprs));
            }
        };
    }

    private static @Nullable MolangExpr optimizeMathCall(@NotNull String function, @NotNull List<MolangExpr> args) {
        if (MolangMath.IMPURE_METHODS.contains(function)) return null;

        // All the args must be constant numbers, then we will just call the relevant math function.
        var argNumbers = new ArrayList<MolangValue>();
        for (MolangExpr arg : args) {
            if (arg instanceof MolangExpr.Num(double value)) {
                argNumbers.add(new MolangValue.Num(value));
                continue;
            }

            // If any argument is not a number, we cannot optimize this call.
            return null;
        }

        final var func = MolangMath.MODULE.get(function);
        if (func == MolangValue.NIL) {
            // Function not found, cannot optimize.
            return null;
        }

        // Call the math function with the optimized arguments.
        try {
            final MolangValue result = ((MolangValue.Function) func).apply(argNumbers);
            return result instanceof MolangValue.Num(double value)
                    ? new MolangExpr.Num(value) : null;
        } catch (Exception ignored) {
            return null; // Let it error at runtime properly
        }
    }
}
