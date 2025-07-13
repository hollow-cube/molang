package net.hollowcube.molang.eval;

import net.hollowcube.molang.MolangExpr;
import net.hollowcube.molang.runtime.ContentError;
import net.hollowcube.molang.runtime.MolangMath;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class MolangEvaluator {
    public static final int MAX_LOOP_COUNTER = 1024; // Arbitrary limit to prevent massive loops.

    private static final MolangValue.Function LOOP_FUNC = ignored -> {
        throw new IllegalStateException("loop intrinsic"); // Unreachable.
    };

    private final List<ContentError> errors = new ArrayList<>();
    private boolean loopScope = false; // Whether we are currently in a looping scope (to catch break/continues)
    private int loopCounter = MAX_LOOP_COUNTER;

    private final MutableHolderImpl variable = new MutableHolderImpl();
    private final MutableHolderImpl temp = new MutableHolderImpl();
    private final MolangValue.Holder root;

    public MolangEvaluator(@NotNull Map<String, MolangValue> initial) {
        var entries = new HashMap<>(initial);
        entries.putIfAbsent("variable", variable);
        entries.putIfAbsent("v", variable);
        entries.putIfAbsent("temp", temp);
        entries.putIfAbsent("t", temp);
        entries.putIfAbsent("math", MolangMath.MODULE);
        entries.putIfAbsent("m", MolangMath.MODULE);
        this.root = new HolderImpl(Map.copyOf(entries));
    }

    public double eval(@NotNull MolangExpr expr) {
        loopCounter = MAX_LOOP_COUNTER;
        errors.clear();
        temp.clear();
        final MolangValue value = Return.catching(() -> evalExpr(expr));
        return unwrapNumber(value, () -> "Expected number, got: " + value);
    }

    public boolean evalBool(@NotNull MolangExpr expr) {
        return eval(expr) != 0.0;
    }

    public @NotNull MolangValue getVariable(@NotNull String name) {
        return variable.get(name);
    }

    private @NotNull MolangValue evalExpr(@NotNull MolangExpr expr) {
        try {
            return switch (expr) {
                case MolangExpr.Num num -> new MolangValue.Num(num.value());
                case MolangExpr.Str str -> new MolangValue.Str(str.value());
                case MolangExpr.Ident ident -> evalIdent(ident);
                case MolangExpr.Access access -> evalAccess(access);
                case MolangExpr.Unary unary -> evalUnary(unary);
                case MolangExpr.Binary binary -> evalBinary(binary);
                case MolangExpr.Ternary ternary -> evalTernary(ternary);
                case MolangExpr.Block block -> evalBlock(block);
                case MolangExpr.Call call -> evalCall(call);
            };
        } catch (Break | Continue value) {
            if (!loopScope) {
                // Outside of a loop break and continue just result in a content error.
                errors.add(new ContentError(
                        "Cannot use " + (value instanceof Break ? "break" : "continue") + " outside of a loop"));
                return MolangValue.NIL;
            }
            throw value;
        }
    }

    private @NotNull MolangValue evalIdent(@NotNull MolangExpr.Ident ident) {
        return switch (ident.value()) {
            case "continue" -> throw new Continue();
            case "break" -> throw new Break();
            // TODO: return value? probably will end up as its own expr
            case "return" -> throw new Return(MolangValue.NIL);
            case "loop" -> LOOP_FUNC;
            case "for_each" -> {
                this.errors.add(new ContentError("'for_each' expressions are not supported"));
                yield MolangValue.NIL;
            }
            case "this" -> {
                this.errors.add(new ContentError("'this' expressions are not supported"));
                yield MolangValue.NIL;
            }
            default -> root.get(ident.value());
        };
    }

    private @NotNull MolangValue evalAccess(@NotNull MolangExpr.Access access) {
        final MolangValue lhs = evalExpr(access.lhs());
        if (!(lhs instanceof MolangValue.Holder holder)) {
            errors.add(new ContentError("Cannot access field '" + access.field() + "' on: " + lhs));
            return MolangValue.NIL;
        }

        final MolangValue value = holder.get(access.field());
        // If the value is a function, we should call it with zero args.
        if (value instanceof MolangValue.Function func)
            return evalCallInternal(func, List.of());
        return value;
    }

    private @NotNull MolangValue evalUnary(@NotNull MolangExpr.Unary unary) {
        var rhs = evalExpr(unary.rhs());
        return switch (unary.op()) {
            case NEGATE -> new MolangValue.Num(-unwrapNumber(rhs,
                                                             () -> "Cannot apply unary '-' to: " + rhs));
            case NOT -> new MolangValue.Num(!unwrapBoolean(rhs,
                                                           () -> "Cannot apply '!' to: " + rhs) ? 1.0 : 0.0);
        };
    }

    private @NotNull MolangValue evalBinary(@NotNull MolangExpr.Binary binary) {
        final MolangValue lhs = evalExpr(binary.lhs());
        if (binary.op() == MolangExpr.Binary.Op.NULL_COALESCE) {
            if (lhs instanceof MolangValue.Nil)
                return evalExpr(binary.rhs());
            return lhs;
        }

        final MolangValue rhs = evalExpr(binary.rhs());
        Supplier<String> error = () -> "Cannot apply operator: " + lhs + " " + binary.op().symbol() + " " + rhs;
        return switch (binary.op()) {
            case PLUS -> new MolangValue.Num(unwrapNumber(lhs, error) + unwrapNumber(rhs, error));
            case MINUS -> new MolangValue.Num(unwrapNumber(lhs, error) - unwrapNumber(rhs, error));
            case DIV -> {
                ;
                double rhsValue = unwrapNumber(rhs, error);
                if (rhsValue == 0.0) {
                    errors.add(new ContentError("Division by zero: " + lhs + " / " + rhs));
                    yield new MolangValue.Num(0.0);
                }
                yield new MolangValue.Num(unwrapNumber(lhs, error) / rhsValue);
            }
            case MUL -> new MolangValue.Num(unwrapNumber(lhs, error) * unwrapNumber(rhs, error));
            case GTE -> new MolangValue.Num(unwrapNumber(lhs, error) >= unwrapNumber(rhs, error));
            case GT -> new MolangValue.Num(unwrapNumber(lhs, error) > unwrapNumber(rhs, error));
            case LTE -> new MolangValue.Num(unwrapNumber(lhs, error) <= unwrapNumber(rhs, error));
            case LT -> new MolangValue.Num(unwrapNumber(lhs, error) < unwrapNumber(rhs, error));
            case EQ, NEQ -> {
                // If either is a number, then both must be.
                if (lhs instanceof MolangValue.Num || rhs instanceof MolangValue.Num) {
                    double lhsValue = unwrapNumber(lhs, error);
                    double rhsValue = unwrapNumber(rhs, error);
                    yield new MolangValue.Num(binary.op() == MolangExpr.Binary.Op.EQ
                                                      ? lhsValue == rhsValue : lhsValue != rhsValue);
                }
                // If both are strings, compare them
                if (lhs instanceof MolangValue.Str(var lhsValue) && rhs instanceof MolangValue.Str(var rhsValue)) {
                    yield new MolangValue.Num((binary.op() == MolangExpr.Binary.Op.EQ) == lhsValue.equals(rhsValue));
                }
                // Otherwise we do not know how to compare.
                errors.add(new ContentError("Cannot apply operator: " + lhs + " " + binary.op().symbol() + " " + rhs));
                yield new MolangValue.Num(0);
            }
            // We already handled NULL_COALESCE above (to avoid evaluating rhs)
            case NULL_COALESCE -> throw new UnsupportedOperationException("unreachable");
        };
    }

    private @NotNull MolangValue evalTernary(@NotNull MolangExpr.Ternary ternary) {
        final MolangValue conditionValue = evalExpr(ternary.cond());
        final boolean condition = unwrapBoolean(conditionValue,
                                                () -> "Condition must be a number, not: " + ternary);
        return condition ? evalExpr(ternary.thenExpr()) : evalExpr(ternary.elseExpr());
    }

    private @NotNull MolangValue evalBlock(@NotNull MolangExpr.Block block) {
        for (var expr : block.exprs())
            evalExpr(expr); // Eval and ignore the results
        return MolangValue.NIL;
    }

    private @NotNull MolangValue evalCall(@NotNull MolangExpr.Call call) {
        final MolangValue lhs = switch (call.lhs()) {
            case MolangExpr.Access access -> evalExpr(access.lhs()) instanceof MolangValue.Holder holder
                    ? holder.get(access.field()) : MolangValue.NIL;
            default -> evalExpr(call.lhs());
        };
        if (lhs == LOOP_FUNC) return evalLoop(call.args());
        if (!(lhs instanceof MolangValue.Function func)) {
            errors.add(new ContentError("Cannot call non-function: " + lhs));
            return MolangValue.NIL;
        }

        var args = new ArrayList<MolangValue>();
        for (var arg : call.args()) {
            var value = evalExpr(arg);
            if (value instanceof MolangValue.Nil) {
                errors.add(new ContentError("Cannot pass 'nil' as an argument to a function: " + call));
                args.add(new MolangValue.Num(0.0)); // Replace nil with 0.0
            }
            args.add(value);
        }

        return evalCallInternal(func, args);
    }

    private @NotNull MolangValue evalCallInternal(@NotNull MolangValue.Function func, @NotNull List<MolangValue> args) {
        try {
            return func.apply(args);
        } catch (Exception e) {
            errors.add(new ContentError("Error while calling function: " + func + ": " + e.getMessage()));
            return MolangValue.NIL;
        }
    }

    private @NotNull MolangValue evalLoop(@NotNull List<MolangExpr> args) {
        if (args.size() != 2) {
            errors.add(new ContentError("loop requires exactly 2 arguments, got: " + args.size()));
            return MolangValue.NIL;
        }
        final MolangValue iterCountValue = evalExpr(args.getFirst());
        final int iterCount = (int) unwrapNumber(iterCountValue,
                                                 () -> "loop requires a number as the first argument, got: " + iterCountValue);
        if (!(args.getLast() instanceof MolangExpr.Block)) {
            errors.add(new ContentError("loop requires a block as the second argument, got: " + args.getLast()));
            return MolangValue.NIL;
        }

        var lastLoopScope = loopScope;
        loopScope = true; // Enter loop
        try {
            for (int i = 0; i < iterCount; i++) {
                if (loopCounter-- <= 0) {
                    errors.add(new ContentError("Loop counter exceeded maximum limit of " + MAX_LOOP_COUNTER));
                    return MolangValue.NIL;
                }

                try {
                    evalExpr(args.getLast());
                } catch (Break e) {
                    break;
                } catch (Continue ignored) {
                    // Continue to the next iteration
                }
            }
        } finally {
            loopScope = lastLoopScope; // Exit loop
        }
        return MolangValue.NIL;
    }

    private boolean unwrapBoolean(@NotNull MolangValue value, @NotNull Supplier<String> errorSupplier) {
        return unwrapNumber(value, errorSupplier) != 0.0;
    }

    private double unwrapNumber(@NotNull MolangValue value, @NotNull Supplier<String> errorSupplier) {
        if (value instanceof MolangValue.Num(double val))
            return val;
        errors.add(new ContentError(errorSupplier.get()));
        return 0.0;
    }

    // Control flow exceptions below

    private static class Break extends RuntimeException {
    }

    private static class Continue extends RuntimeException {
    }

    private static class Return extends RuntimeException {
        public static @NotNull MolangValue catching(@NotNull Supplier<MolangValue> supplier) {
            try {
                return supplier.get();
            } catch (Return ret) {
                return ret.value;
            }
        }

        private final MolangValue value;

        public Return(@NotNull MolangValue value) {
            this.value = value;
        }
    }

    // Other builtins

    record HolderImpl(@NotNull Map<String, MolangValue> entries) implements MolangValue.Holder {
        @Override
        public @NotNull MolangValue get(@NotNull String field) {
            return entries.getOrDefault(field, MolangValue.NIL);
        }
    }

    static class MutableHolderImpl implements MolangValue.Holder.Mutable {
        private final Map<String, MolangValue> state = new HashMap<>();

        @Override
        public void set(@NotNull String field, @NotNull MolangValue value) {
            state.put(field, value);
        }

        @Override
        public @NotNull MolangValue get(@NotNull String field) {
            return state.getOrDefault(field, MolangValue.NIL);
        }

        public void clear() {
            state.clear();
        }
    }
}
