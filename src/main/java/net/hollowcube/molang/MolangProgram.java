package net.hollowcube.molang;

import net.hollowcube.molang.MolangEnvironment.Entry;
import net.hollowcube.molang.Symbols.Symbol;
import net.hollowcube.molang.runtime.ContentError;
import net.hollowcube.molang.runtime.MolangContentException;
import net.hollowcube.molang.runtime.MolangMath;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/// A compiled Molang expression.
///
/// Programs are immutable and may be shared between states and threads.
public final class MolangProgram<T> {
    private final Node root;
    private final boolean empty;
    private final Analysis analysis;
    private final @Nullable String source;

    private MolangProgram(Node root, boolean empty, Analysis analysis, @Nullable String source) {
        this.root = root;
        this.empty = empty;
        this.analysis = analysis;
        this.source = source;
    }

    /// Compiles source that reads no host values, against [MolangEnvironment#empty()].
    ///
    /// @throws MolangParseException if the source is not a valid expression
    public static MolangProgram<Object> compile(String source) {
        return MolangEnvironment.empty().compile(source);
    }

    /// @param source the source `expr` was parsed from, which content errors then include, or null
    static <T> MolangProgram<T> compile(
        MolangEnvironment<T> environment,
        MolangExpr expr,
        @Nullable String source,
        boolean optimize
    ) {
        boolean empty = expr instanceof MolangExpr.Block(var exprs, _) && exprs.isEmpty();
        var compiler = new Compiler(environment, optimize);
        Node root = compiler.compile(expr);
        // Returning a number from the top level is the same as being that number
        if (optimize && root instanceof Return ret && ret.value instanceof NumConst k) root = k;
        var constant = !empty && root instanceof NumConst k
            ? OptionalDouble.of(k.value)
            : OptionalDouble.empty();
        return new MolangProgram<>(root, empty, compiler.analysis(constant), source);
    }

    /// Evaluates the program with a host context, which the environment's functions receive.
    /// Content errors it reports are available from [MolangState#getErrors()] until the next evaluation.
    public double eval(MolangState state, T context) {
        state.begin(source);
        if (empty) {
            state.error("Empty expression", 0);
            return 0.0;
        }
        try {
            return root.num(state, context);
        } catch (MolangState.Return ret) {
            return unwrap(state, ret.value, ret.position);
        }
    }

    /// Evaluates the program without a context, for environments whose functions do not use one.
    public double eval(MolangState state) {
        return eval(state, null);
    }

    public boolean evalBool(MolangState state, T context) {
        return eval(state, context) != 0.0;
    }

    public boolean evalBool(MolangState state) {
        return eval(state) != 0.0;
    }

    /// What the program reads, writes and depends on, worked out while compiling.
    public Analysis analysis() {
        return analysis;
    }

    /// What a program reads, writes and depends on, eg to decide whether its results can be computed
    /// once and cached. It is conservative: a name in a branch that folding removed is still reported.
    ///
    /// Names are canonical and lowercase, so `q.anim_time` is reported as `query.anim_time` and
    /// `v.x` as `variable.x`.
    ///
    /// @param constant         the program's value if it is constant (or was folded into a constant).
    ///                         Its still safe to eval in this case, but you may be able to optimize
    ///                         further.
    /// @param hostNames        the names bound to host values or functions that the program reads or
    ///                         calls, eg `query.anim_time`. Pure functions and constants are not
    ///                         included, since they do not depend on the context.
    /// @param dynamicNames     the names answered by dynamic resolvers, eg `query.props.owner`
    /// @param unresolvedNames  the names the environment does not have (eg `query.typo`), which
    ///                         evaluate to null, so a loader may want to warn about them
    /// @param variablesRead    the state's own variables the program reads, eg `variable.speed`
    /// @param variablesWritten the state's own variables the program assigns
    /// @param usesTemps        whether the program reads or writes any `temp`
    /// @param random           whether the program calls a random `math` function, so evaluating it
    ///                         twice with the same inputs may give different results
    public record Analysis(
        OptionalDouble constant,
        Set<String> hostNames,
        Set<String> dynamicNames,
        Set<String> unresolvedNames,
        Set<String> variablesRead,
        Set<String> variablesWritten,
        boolean usesTemps,
        boolean random
    ) {
        /// Whether the program's result depends only on the given host names, eg `query.anim_time`:
        /// it reads no other host or dynamic names, no variables, and calls no random functions.
        /// Assignments are side effects this does not consider: check [#variablesWritten()] before
        /// skipping an evaluation.
        public boolean dependsOnlyOn(Set<String> names) {
            return names.containsAll(hostNames)
                && dynamicNames.isEmpty()
                && variablesRead.isEmpty()
                && !random;
        }
    }

    // Nodes

    /// A compiled expression. `num` is `value` checked to be a number, reporting a content error (and
    /// giving 0) if it is not one; numeric nodes implement `num` and box only when asked for a value.
    private abstract static class Node {
        /// Where in the source the node's expression is, for content errors; -1 if unknown.
        int position = -1;

        abstract MolangValue value(MolangState s, Object c);

        double num(MolangState s, Object c) {
            return unwrap(s, value(s, c), position);
        }

        /// Evaluates for side effects only, as a statement whose value is unused.
        void exec(MolangState s, Object c) {
            value(s, c);
        }

        /// Whether [#value] is always a number, so [#num] can never report an error.
        boolean numeric() {
            return false;
        }
    }

    private abstract static class NumNode extends Node {
        @Override
        abstract double num(MolangState s, Object c);

        @Override
        MolangValue value(MolangState s, Object c) {
            return new MolangValue.Num(num(s, c));
        }

        @Override
        void exec(MolangState s, Object c) {
            num(s, c);
        }

        @Override
        boolean numeric() {
            return true;
        }
    }

    private static final class NumConst extends NumNode {
        final double value;
        final MolangValue boxed;

        NumConst(double value) {
            this.value = value;
            this.boxed = new MolangValue.Num(value);
        }

        @Override
        double num(MolangState s, Object c) {
            return value;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            return boxed;
        }
    }

    /// A constant that is not a number: a string, or null.
    private static final class ValueConst extends Node {
        final MolangValue value;

        ValueConst(MolangValue value) {
            this.value = value;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            return value;
        }
    }

    /// Evaluates its operands for their side effects, then reports a content error. It gives null,
    /// or 0 for a construct that always gives a number (eg an invalid assignment), so the error is
    /// reported once rather than again when the result is used as a number.
    private static final class ErrorNode extends Node {
        private static final MolangValue ZERO = new MolangValue.Num(0.0);

        final String message;
        final MolangValue result;
        final Node[] operands;

        ErrorNode(String message, Node... operands) {
            this(message, MolangValue.NULL, operands);
        }

        private ErrorNode(String message, MolangValue result, Node... operands) {
            this.message = message;
            this.result = result;
            this.operands = operands;
        }

        static ErrorNode zero(String message) {
            return new ErrorNode(message, ZERO);
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            for (Node operand : operands) operand.exec(s, c);
            s.error(message, position);
            return result;
        }

        @Override
        boolean numeric() {
            return result != MolangValue.NULL;
        }
    }

    /// `break` or `continue`, which are content errors outside of a loop.
    private static final class Jump extends Node {
        final RuntimeException signal;
        final String name;

        Jump(RuntimeException signal, String name) {
            this.signal = signal;
            this.name = name;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            if (!s.loopScope) {
                s.error("Cannot use " + name + " outside of a loop", position);
                return MolangValue.NULL;
            }
            throw signal;
        }
    }

    /// `variable.name` or `temp.name`.
    private static final class Variable extends Node {
        final boolean temp;
        final Symbol name;

        Variable(boolean temp, Symbol name) {
            this.temp = temp;
            this.name = name;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            return (temp ? s.temp : s.variable).get(name);
        }

        @Override
        double num(MolangState s, Object c) {
            // getNumber gives NaN when the variable does not hold a number (it is unset, or a string),
            // so only then take the slow path, which reports it. A variable that holds NaN also takes
            // it, and still gives NaN.
            double value = (temp ? s.temp : s.variable).getNumber(name);
            return !Double.isNaN(value) ? value : unwrap(s, value(s, c), position);
        }
    }

    private static final class VariableAssign extends NumNode {
        final boolean temp;
        final Symbol name;
        final Node value;

        VariableAssign(boolean temp, Symbol name, Node value) {
            this.temp = temp;
            this.name = name;
            this.value = value;
        }

        @Override
        double num(MolangState s, Object c) {
            VariableStore store = temp ? s.temp : s.variable;
            if (value.numeric()) store.setNumber(name, value.num(s, c));
            else store.set(name, value.value(s, c));
            return 0.0;
        }
    }

    private static final class Negate extends NumNode {
        final Node rhs;

        Negate(Node rhs) {
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return -rhs.num(s, c);
        }
    }

    /// Unary `+`, which gives its operand as a number.
    private static final class Plus extends NumNode {
        final Node rhs;

        Plus(Node rhs) {
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return rhs.num(s, c);
        }
    }

    private static final class Not extends NumNode {
        final Node rhs;

        Not(Node rhs) {
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return rhs.num(s, c) != 0.0 ? 0.0 : 1.0;
        }
    }

    private static final class Add extends NumNode {
        final Node lhs, rhs;

        Add(Node lhs, Node rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return lhs.num(s, c) + rhs.num(s, c);
        }
    }

    private static final class AddConst extends NumNode {
        final Node lhs;
        final double rhs;

        AddConst(Node lhs, double rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return lhs.num(s, c) + rhs;
        }
    }

    private static final class Sub extends NumNode {
        final Node lhs, rhs;

        Sub(Node lhs, Node rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return lhs.num(s, c) - rhs.num(s, c);
        }
    }

    private static final class SubConst extends NumNode {
        final Node lhs;
        final double rhs;

        SubConst(Node lhs, double rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return lhs.num(s, c) - rhs;
        }
    }

    private static final class ConstSub extends NumNode {
        final double lhs;
        final Node rhs;

        ConstSub(double lhs, Node rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return lhs - rhs.num(s, c);
        }
    }

    private static final class Mul extends NumNode {
        final Node lhs, rhs;

        Mul(Node lhs, Node rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return lhs.num(s, c) * rhs.num(s, c);
        }
    }

    private static final class MulConst extends NumNode {
        final Node lhs;
        final double rhs;

        MulConst(Node lhs, double rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return lhs.num(s, c) * rhs;
        }
    }

    private static final class Div extends NumNode {
        final Node lhs, rhs;

        Div(Node lhs, Node rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            double l = lhs.num(s, c), r = rhs.num(s, c);
            if (r == 0.0) {
                s.error("Division by zero", position);
                return 0.0;
            }
            return l / r;
        }
    }

    private static final class DivConst extends NumNode {
        final Node lhs;
        final double rhs; // never zero

        DivConst(Node lhs, double rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            return lhs.num(s, c) / rhs;
        }
    }

    private static final class Compare extends NumNode {
        final MolangExpr.Binary.Op op;
        final Node lhs, rhs;

        Compare(MolangExpr.Binary.Op op, Node lhs, Node rhs) {
            this.op = op;
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            double l = lhs.num(s, c), r = rhs.num(s, c);
            boolean result = switch (op) {
                case GTE -> l >= r;
                case GT -> l > r;
                case LTE -> l <= r;
                case LT -> l < r;
                case EQ -> l == r;
                case NEQ -> l != r;
                default -> throw new IllegalStateException("not a comparison: " + op);
            };
            return result ? 1.0 : 0.0;
        }
    }

    /// `&&` or `||`, which only evaluate their right side when the left does not decide the result.
    private static final class Logic extends NumNode {
        final boolean and;
        final Node lhs, rhs;

        Logic(boolean and, Node lhs, Node rhs) {
            this.and = and;
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            if ((lhs.num(s, c) != 0.0) != and) return and ? 0.0 : 1.0;
            return rhs.num(s, c) != 0.0 ? 1.0 : 0.0;
        }
    }

    /// `==` or `!=` where either side may not be a number: numbers compare with numbers and strings
    /// with strings, and anything else is a content error.
    private static final class Equals extends NumNode {
        final boolean equal;
        final Node lhs, rhs;

        Equals(boolean equal, Node lhs, Node rhs) {
            this.equal = equal;
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        double num(MolangState s, Object c) {
            MolangValue l = lhs.value(s, c), r = rhs.value(s, c);
            if (l instanceof MolangValue.Num || r instanceof MolangValue.Num) {
                double lv = unwrap(s, l, lhs.position), rv = unwrap(s, r, rhs.position);
                return (equal ? lv == rv : lv != rv) ? 1.0 : 0.0;
            }
            if (l instanceof MolangValue.Str(var ls) && r instanceof MolangValue.Str(var rs))
                return equal == ls.equals(rs) ? 1.0 : 0.0;
            s.error("Cannot compare " + l + " and " + r, position);
            return 0.0;
        }
    }

    private static final class Coalesce extends Node {
        final Node lhs, rhs;

        Coalesce(Node lhs, Node rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            MolangValue l = lhs.value(s, c);
            return l instanceof MolangValue.Null ? rhs.value(s, c) : l;
        }
    }

    private static final class NumTernary extends NumNode {
        final Node cond, then, otherwise;

        NumTernary(Node cond, Node then, Node otherwise) {
            this.cond = cond;
            this.then = then;
            this.otherwise = otherwise;
        }

        @Override
        double num(MolangState s, Object c) {
            return cond.num(s, c) != 0.0 ? then.num(s, c) : otherwise.num(s, c);
        }
    }

    private static final class Ternary extends Node {
        final Node cond, then, otherwise;

        Ternary(Node cond, Node then, Node otherwise) {
            this.cond = cond;
            this.then = then;
            this.otherwise = otherwise;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            return cond.num(s, c) != 0.0 ? then.value(s, c) : otherwise.value(s, c);
        }

        @Override
        double num(MolangState s, Object c) {
            return cond.num(s, c) != 0.0 ? then.num(s, c) : otherwise.num(s, c);
        }

        @Override
        void exec(MolangState s, Object c) {
            if (cond.num(s, c) != 0.0) then.exec(s, c);
            else otherwise.exec(s, c);
        }
    }

    private static final class Block extends Node {
        final Node[] statements;
        final Node last;

        Block(Node[] statements) {
            this.statements = Arrays.copyOf(statements, statements.length - 1);
            this.last = statements[statements.length - 1];
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            for (Node statement : statements) statement.exec(s, c);
            return last.value(s, c);
        }

        @Override
        double num(MolangState s, Object c) {
            for (Node statement : statements) statement.exec(s, c);
            return last.num(s, c);
        }

        @Override
        void exec(MolangState s, Object c) {
            for (Node statement : statements) statement.exec(s, c);
            last.exec(s, c);
        }

        @Override
        boolean numeric() {
            return last.numeric();
        }
    }

    private static final class Return extends Node {
        final Node value;
        /// The signal to throw when the value is constant, so returning it allocates nothing.
        final MolangState.@Nullable Return constant;

        Return(Node value) {
            this.value = value;
            this.constant = switch (value) {
                // Only +0.0 is the shared empty return; -0.0 must keep its sign
                case NumConst k when Double.doubleToRawLongBits(k.value)
                    == 0L -> MolangState.Return.EMPTY;
                case NumConst k -> new MolangState.Return(k.boxed, value.position);
                case ValueConst k -> new MolangState.Return(k.value, value.position);
                default -> null;
            };
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            if (constant != null) throw constant;
            throw new MolangState.Return(value.value(s, c), value.position);
        }
    }

    private static final class Loop extends Node {
        final Node[] args;
        final boolean lastArgIsBlock;

        Loop(Node[] args, boolean lastArgIsBlock) {
            this.args = args;
            this.lastArgIsBlock = lastArgIsBlock;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            if (args.length != 2) {
                s.error("loop requires exactly 2 arguments, got: " + args.length, position);
                return MolangValue.NULL;
            }
            int count = (int) args[0].num(s, c);
            if (!lastArgIsBlock) {
                s.error("loop requires a block as the second argument", position);
                return MolangValue.NULL;
            }
            boolean lastLoopScope = s.loopScope;
            s.loopScope = true;
            try {
                for (int i = 0; i < count; i++) {
                    if (s.loopCounter-- <= 0) {
                        s.error(
                            "Loop counter exceeded maximum limit of "
                                + MolangState.MAX_LOOP_COUNTER,
                            position
                        );
                        return MolangValue.NULL;
                    }
                    try {
                        args[1].exec(s, c);
                    } catch (MolangState.Break ignored) {
                        break;
                    } catch (MolangState.Continue ignored) {
                        // Next iteration
                    }
                }
            } finally {
                s.loopScope = lastLoopScope;
            }
            return MolangValue.NULL;
        }
    }
    // Calls of math and host functions

    /// A call of a function with numeric parameters (or of a query, which has none).
    private abstract static class Call extends NumNode {
        final String name;
        final Node[] args;

        Call(String name, Node[] args) {
            this.name = name;
            this.args = args;
        }

        /// An argument for a numeric parameter: a non-number is a content error and 0.
        final double arg(MolangState s, Object c, int index) {
            Node arg = args[index];
            if (arg.numeric()) return arg.num(s, c);
            MolangValue value = arg.value(s, c);
            if (value instanceof MolangValue.Num(double number)) return number;
            s.error(
                "Expected a number for argument " + (index + 1) + " of " + name + ", got " + value,
                arg.position
            );
            return 0.0;
        }

        final double failed(MolangState s, RuntimeException ex) {
            reportFailure(s, name, ex, position);
            return 0.0;
        }
    }

    /// A call with the wrong number of arguments: they are still evaluated, then it is an error.
    private static final class Arity extends Call {
        final int expected;

        Arity(String name, Node[] args, int expected) {
            super(name, args);
            this.expected = expected;
        }

        @Override
        double num(MolangState s, Object c) {
            for (Node arg : args) arg.exec(s, c);
            s.error(
                "Expected " + expected + " arguments but got " + args.length + " for " + name,
                position
            );
            return 0.0;
        }
    }

    private static final class Math1 extends Call {
        final int op;

        Math1(String name, Node[] args, int op) {
            super(name, args);
            this.op = op;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0);
            try {
                return switch (op) {
                    case ABS -> MolangMath.abs(a);
                    case ACOS -> MolangMath.acos(a);
                    case ASIN -> MolangMath.asin(a);
                    case ATAN -> MolangMath.atan(a);
                    case CEIL -> MolangMath.ceil(a);
                    case COS -> MolangMath.cos(a);
                    case EXP -> MolangMath.exp(a);
                    case FLOOR -> MolangMath.floor(a);
                    case HERMITE_BLEND -> MolangMath.hermiteBlend(a);
                    case LN -> MolangMath.ln(a);
                    case MIN_ANGLE -> MolangMath.minAngle(a);
                    case ROUND -> MolangMath.round(a);
                    case SIGN -> MolangMath.sign(a);
                    case SIN -> MolangMath.sin(a);
                    case SQRT -> MolangMath.sqrt(a);
                    case TRUNC -> MolangMath.trunc(a);
                    default -> throw new IllegalStateException("unary math op " + op);
                };
            } catch (MolangContentException ex) {
                return failed(s, ex);
            }
        }
    }

    private static final class Math2 extends Call {
        final int op;

        Math2(String name, Node[] args, int op) {
            super(name, args);
            this.op = op;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0), b = arg(s, c, 1);
            try {
                return switch (op) {
                    case ATAN2 -> MolangMath.atan2(a, b);
                    case COPY_SIGN -> MolangMath.copySign(a, b);
                    case MAX -> MolangMath.max(a, b);
                    case MIN -> MolangMath.min(a, b);
                    case MOD -> MolangMath.mod(a, b);
                    case POW -> MolangMath.pow(a, b);
                    case RANDOM -> MolangMath.random(a, b);
                    case RANDOM_INTEGER -> MolangMath.randomInteger(a, b);
                    default -> throw new IllegalStateException("binary math op " + op);
                };
            } catch (MolangContentException ex) {
                return failed(s, ex);
            }
        }
    }

    private static final class Math3 extends Call {
        final int op;

        Math3(String name, Node[] args, int op) {
            super(name, args);
            this.op = op;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0), b = arg(s, c, 1), c1 = arg(s, c, 2);
            try {
                return switch (op) {
                    case CLAMP -> MolangMath.clamp(a, b, c1);
                    case DIE_ROLL -> MolangMath.dieRoll(a, b, c1);
                    case DIE_ROLL_INTEGER -> MolangMath.dieRollInteger(a, b, c1);
                    case EASE_IN_BACK -> MolangMath.easeInBack(a, b, c1);
                    case EASE_IN_BOUNCE -> MolangMath.easeInBounce(a, b, c1);
                    case EASE_IN_CIRC -> MolangMath.easeInCirc(a, b, c1);
                    case EASE_IN_CUBIC -> MolangMath.easeInCubic(a, b, c1);
                    case EASE_IN_ELASTIC -> MolangMath.easeInElastic(a, b, c1);
                    case EASE_IN_EXPO -> MolangMath.easeInExpo(a, b, c1);
                    case EASE_IN_OUT_BACK -> MolangMath.easeInOutBack(a, b, c1);
                    case EASE_IN_OUT_BOUNCE -> MolangMath.easeInOutBounce(a, b, c1);
                    case EASE_IN_OUT_CIRC -> MolangMath.easeInOutCirc(a, b, c1);
                    case EASE_IN_OUT_CUBIC -> MolangMath.easeInOutCubic(a, b, c1);
                    case EASE_IN_OUT_ELASTIC -> MolangMath.easeInOutElastic(a, b, c1);
                    case EASE_IN_OUT_EXPO -> MolangMath.easeInOutExpo(a, b, c1);
                    case EASE_IN_OUT_QUAD -> MolangMath.easeInOutQuad(a, b, c1);
                    case EASE_IN_OUT_QUART -> MolangMath.easeInOutQuart(a, b, c1);
                    case EASE_IN_OUT_QUINT -> MolangMath.easeInOutQuint(a, b, c1);
                    case EASE_IN_OUT_SINE -> MolangMath.easeInOutSine(a, b, c1);
                    case EASE_IN_QUAD -> MolangMath.easeInQuad(a, b, c1);
                    case EASE_IN_QUART -> MolangMath.easeInQuart(a, b, c1);
                    case EASE_IN_QUINT -> MolangMath.easeInQuint(a, b, c1);
                    case EASE_IN_SINE -> MolangMath.easeInSine(a, b, c1);
                    case EASE_OUT_BACK -> MolangMath.easeOutBack(a, b, c1);
                    case EASE_OUT_BOUNCE -> MolangMath.easeOutBounce(a, b, c1);
                    case EASE_OUT_CIRC -> MolangMath.easeOutCirc(a, b, c1);
                    case EASE_OUT_CUBIC -> MolangMath.easeOutCubic(a, b, c1);
                    case EASE_OUT_ELASTIC -> MolangMath.easeOutElastic(a, b, c1);
                    case EASE_OUT_EXPO -> MolangMath.easeOutExpo(a, b, c1);
                    case EASE_OUT_QUAD -> MolangMath.easeOutQuad(a, b, c1);
                    case EASE_OUT_QUART -> MolangMath.easeOutQuart(a, b, c1);
                    case EASE_OUT_QUINT -> MolangMath.easeOutQuint(a, b, c1);
                    case EASE_OUT_SINE -> MolangMath.easeOutSine(a, b, c1);
                    case INVERSE_LERP -> MolangMath.inverseLerp(a, b, c1);
                    case LERP -> MolangMath.lerp(a, b, c1);
                    case LERPROTATE -> MolangMath.lerprotate(a, b, c1);
                    default -> throw new IllegalStateException("ternary math op " + op);
                };
            } catch (MolangContentException ex) {
                return failed(s, ex);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class NumberQuery extends Call {
        final ToDoubleFunction function;

        NumberQuery(String name, ToDoubleFunction<?> function) {
            super(name, new Node[0]);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            try {
                return function.applyAsDouble(c);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class BoolQuery extends Call {
        final Predicate function;

        BoolQuery(String name, Predicate<?> function) {
            super(name, new Node[0]);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            try {
                return function.test(c) ? 1.0 : 0.0;
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class OptionalNumberQuery extends Node {
        final String name;
        final java.util.function.Function function;

        OptionalNumberQuery(String name, java.util.function.Function<?, Double> function) {
            this.name = name;
            this.function = function;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            try {
                Object value = function.apply(c);
                return value == null ? MolangValue.NULL : new MolangValue.Num((Double) value);
            } catch (RuntimeException ex) {
                reportFailure(s, name, ex, position);
                return MolangValue.NULL;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class TextQuery extends Node {
        final String name;
        final java.util.function.Function function;

        TextQuery(String name, java.util.function.Function<?, String> function) {
            this.name = name;
            this.function = function;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            try {
                Object value = function.apply(c);
                return value == null ? MolangValue.NULL : new MolangValue.Str((String) value);
            } catch (RuntimeException ex) {
                reportFailure(s, name, ex, position);
                return MolangValue.NULL;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class HostCall1 extends Call {
        final MolangEnvironment.Function1 function;

        HostCall1(String name, Node[] args, MolangEnvironment.Function1<?> function) {
            super(name, args);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0);
            try {
                return function.apply(c, a);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class HostCall2 extends Call {
        final MolangEnvironment.Function2 function;

        HostCall2(String name, Node[] args, MolangEnvironment.Function2<?> function) {
            super(name, args);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0), b = arg(s, c, 1);
            try {
                return function.apply(c, a, b);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class HostCall3 extends Call {
        final MolangEnvironment.Function3 function;

        HostCall3(String name, Node[] args, MolangEnvironment.Function3<?> function) {
            super(name, args);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0), b = arg(s, c, 1), c1 = arg(s, c, 2);
            try {
                return function.apply(c, a, b, c1);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class HostCallString extends Call {
        final MolangEnvironment.StringFunction function;

        HostCallString(String name, Node[] args, MolangEnvironment.StringFunction<?> function) {
            super(name, args);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            MolangValue value = args[0].value(s, c);
            if (!(value instanceof MolangValue.Str(String text))) {
                s.error("Expected a string for argument 1 of " + name + ", got " + value, position);
                return 0.0;
            }
            try {
                return function.apply(c, text);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class HostCallVariadic extends Call {
        final MolangEnvironment.VariadicFunction function;

        HostCallVariadic(String name, Node[] args, MolangEnvironment.VariadicFunction<?> function) {
            super(name, args);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            double[] values = new double[args.length];
            for (int i = 0; i < args.length; i++) values[i] = arg(s, c, i);
            try {
                return function.apply(c, values);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    private static final class PureCall0 extends Call {
        final java.util.function.DoubleSupplier function;

        PureCall0(String name, java.util.function.DoubleSupplier function) {
            super(name, new Node[0]);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            try {
                return function.getAsDouble();
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    private static final class PureCall1 extends Call {
        final java.util.function.DoubleUnaryOperator function;

        PureCall1(String name, Node[] args, java.util.function.DoubleUnaryOperator function) {
            super(name, args);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0);
            try {
                return function.applyAsDouble(a);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    private static final class PureCall2 extends Call {
        final java.util.function.DoubleBinaryOperator function;

        PureCall2(String name, Node[] args, java.util.function.DoubleBinaryOperator function) {
            super(name, args);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0), b = arg(s, c, 1);
            try {
                return function.applyAsDouble(a, b);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    private static final class PureCall3 extends Call {
        final MolangEnvironment.PureFunction3 function;

        PureCall3(String name, Node[] args, MolangEnvironment.PureFunction3 function) {
            super(name, args);
            this.function = function;
        }

        @Override
        double num(MolangState s, Object c) {
            double a = arg(s, c, 0), b = arg(s, c, 1), c1 = arg(s, c, 2);
            try {
                return function.apply(a, b, c1);
            } catch (RuntimeException ex) {
                return failed(s, ex);
            }
        }
    }

    /// A call of a [MolangEnvironment.ValueFunction] or [MolangEnvironment.DynamicResolver], which
    /// take and return any values.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class ValueCall extends Node {
        final String name;
        final Node[] args;
        final Object function; // ValueFunction or DynamicResolver
        final String dynamicName;

        ValueCall(String name, Node[] args, Object function, String dynamicName) {
            this.name = name;
            this.args = args;
            this.function = function;
            this.dynamicName = dynamicName;
        }

        @Override
        MolangValue value(MolangState s, Object c) {
            MolangValue[] values = new MolangValue[args.length];
            for (int i = 0; i < args.length; i++) values[i] = args[i].value(s, c);
            List<MolangValue> list = Arrays.asList(values);
            try {
                MolangValue result = function instanceof MolangEnvironment.ValueFunction fn
                    ? fn.apply(c, list)
                    : ((MolangEnvironment.DynamicResolver) function).resolve(c, dynamicName, list);
                return result == null ? MolangValue.NULL : result;
            } catch (RuntimeException ex) {
                reportFailure(s, name, ex, position);
                return MolangValue.NULL;
            }
        }
    }

    // Math function ids, grouped by arity (the hundreds digit)

    private static final int PI = 0;
    private static final int ABS = 100, ACOS = 101, ASIN = 102, ATAN = 103, CEIL = 104, COS = 105, EXP = 106, FLOOR = 107, HERMITE_BLEND = 108, LN = 109, MIN_ANGLE = 110, ROUND = 111, SIGN = 112, SIN = 113, SQRT = 114, TRUNC = 115;
    private static final int ATAN2 = 200, COPY_SIGN = 201, MAX = 202, MIN = 203, MOD = 204, POW = 205, RANDOM = 206, RANDOM_INTEGER = 207;
    private static final int CLAMP = 300, DIE_ROLL = 301, DIE_ROLL_INTEGER = 302, EASE_IN_BACK = 303, EASE_IN_BOUNCE = 304, EASE_IN_CIRC = 305, EASE_IN_CUBIC = 306, EASE_IN_ELASTIC = 307, EASE_IN_EXPO = 308, EASE_IN_OUT_BACK = 309, EASE_IN_OUT_BOUNCE = 310, EASE_IN_OUT_CIRC = 311, EASE_IN_OUT_CUBIC = 312, EASE_IN_OUT_ELASTIC = 313, EASE_IN_OUT_EXPO = 314, EASE_IN_OUT_QUAD = 315, EASE_IN_OUT_QUART = 316, EASE_IN_OUT_QUINT = 317, EASE_IN_OUT_SINE = 318, EASE_IN_QUAD = 319, EASE_IN_QUART = 320, EASE_IN_QUINT = 321, EASE_IN_SINE = 322, EASE_OUT_BACK = 323, EASE_OUT_BOUNCE = 324, EASE_OUT_CIRC = 325, EASE_OUT_CUBIC = 326, EASE_OUT_ELASTIC = 327, EASE_OUT_EXPO = 328, EASE_OUT_QUAD = 329, EASE_OUT_QUART = 330, EASE_OUT_QUINT = 331, EASE_OUT_SINE = 332, INVERSE_LERP = 333, LERP = 334, LERPROTATE = 335;

    /// Every function [MolangMath#MODULE] provides, by the name it is registered under. The
    /// messages in MolangMath's own arity errors name some differently (eg `lerp_rotate`).
    static final Map<String, Integer> MATH_FUNCTIONS = Map.ofEntries(
        Map.entry("pi", PI),
        Map.entry("abs", ABS),
        Map.entry("acos", ACOS),
        Map.entry("asin", ASIN),
        Map.entry("atan", ATAN),
        Map.entry("ceil", CEIL),
        Map.entry("cos", COS),
        Map.entry("exp", EXP),
        Map.entry("floor", FLOOR),
        Map.entry("hermite_blend", HERMITE_BLEND),
        Map.entry("ln", LN),
        Map.entry("min_angle", MIN_ANGLE),
        Map.entry("round", ROUND),
        Map.entry("sign", SIGN),
        Map.entry("sin", SIN),
        Map.entry("sqrt", SQRT),
        Map.entry("trunc", TRUNC),
        Map.entry("atan2", ATAN2),
        Map.entry("copy_sign", COPY_SIGN),
        Map.entry("max", MAX),
        Map.entry("min", MIN),
        Map.entry("mod", MOD),
        Map.entry("pow", POW),
        Map.entry("random", RANDOM),
        Map.entry("random_integer", RANDOM_INTEGER),
        Map.entry("clamp", CLAMP),
        Map.entry("die_roll", DIE_ROLL),
        Map.entry("die_roll_integer", DIE_ROLL_INTEGER),
        Map.entry("ease_in_back", EASE_IN_BACK),
        Map.entry("ease_in_bounce", EASE_IN_BOUNCE),
        Map.entry("ease_in_circ", EASE_IN_CIRC),
        Map.entry("ease_in_cubic", EASE_IN_CUBIC),
        Map.entry("ease_in_elastic", EASE_IN_ELASTIC),
        Map.entry("ease_in_expo", EASE_IN_EXPO),
        Map.entry("ease_in_out_back", EASE_IN_OUT_BACK),
        Map.entry("ease_in_out_bounce", EASE_IN_OUT_BOUNCE),
        Map.entry("ease_in_out_circ", EASE_IN_OUT_CIRC),
        Map.entry("ease_in_out_cubic", EASE_IN_OUT_CUBIC),
        Map.entry("ease_in_out_elastic", EASE_IN_OUT_ELASTIC),
        Map.entry("ease_in_out_expo", EASE_IN_OUT_EXPO),
        Map.entry("ease_in_out_quad", EASE_IN_OUT_QUAD),
        Map.entry("ease_in_out_quart", EASE_IN_OUT_QUART),
        Map.entry("ease_in_out_quint", EASE_IN_OUT_QUINT),
        Map.entry("ease_in_out_sine", EASE_IN_OUT_SINE),
        Map.entry("ease_in_quad", EASE_IN_QUAD),
        Map.entry("ease_in_quart", EASE_IN_QUART),
        Map.entry("ease_in_quint", EASE_IN_QUINT),
        Map.entry("ease_in_sine", EASE_IN_SINE),
        Map.entry("ease_out_back", EASE_OUT_BACK),
        Map.entry("ease_out_bounce", EASE_OUT_BOUNCE),
        Map.entry("ease_out_circ", EASE_OUT_CIRC),
        Map.entry("ease_out_cubic", EASE_OUT_CUBIC),
        Map.entry("ease_out_elastic", EASE_OUT_ELASTIC),
        Map.entry("ease_out_expo", EASE_OUT_EXPO),
        Map.entry("ease_out_quad", EASE_OUT_QUAD),
        Map.entry("ease_out_quart", EASE_OUT_QUART),
        Map.entry("ease_out_quint", EASE_OUT_QUINT),
        Map.entry("ease_out_sine", EASE_OUT_SINE),
        Map.entry("inverse_lerp", INVERSE_LERP),
        Map.entry("lerp", LERP),
        Map.entry("lerprotate", LERPROTATE)
    );

    private static final Set<String> IMPURE_MATH = Set.of(
        "die_roll",
        "die_roll_integer",
        "random",
        "random_integer"
    );

    // Shared behaviour

    /// A value as a number, reporting a content error at `position` (and giving 0) if it is not one.
    static double unwrap(MolangState s, MolangValue value, int position) {
        if (value instanceof MolangValue.Num(double number)) return number;
        s.error("Expected number, got: " + value, position);
        return 0.0;
    }

    private static void reportFailure(
        MolangState s,
        String name,
        RuntimeException ex,
        int position
    ) {
        if (ex instanceof MolangContentException) s.error(ex.getMessage(), position);
        else s.error("Error in " + name + ": " + ex, position);
    }

    private static final class Compiler {
        private final MolangEnvironment<?> environment;
        private final boolean optimize;
        private final Set<String> hostNames = new LinkedHashSet<>();
        private final Set<String> dynamicNames = new LinkedHashSet<>();
        private final Set<String> unresolved = new LinkedHashSet<>();
        private final Set<String> variablesRead = new LinkedHashSet<>();
        private final Set<String> variablesWritten = new LinkedHashSet<>();
        private boolean usesTemps;
        private boolean random;
        private @Nullable MolangState scratch;

        Compiler(MolangEnvironment<?> environment, boolean optimize) {
            this.environment = environment;
            this.optimize = optimize;
        }

        Analysis analysis(OptionalDouble constant) {
            return new Analysis(
                constant,
                Set.copyOf(hostNames),
                Set.copyOf(dynamicNames),
                Set.copyOf(unresolved),
                Set.copyOf(variablesRead),
                Set.copyOf(variablesWritten),
                usesTemps,
                random
            );
        }

        /// Compiles an expression into a node that reports its content errors at the expression's position.
        Node compile(MolangExpr expr) {
            Node node = compileNode(expr);
            if (node.position < 0) node.position = expr.position();
            return node;
        }

        private Node compileNode(MolangExpr expr) {
            return switch (expr) {
                case MolangExpr.Num(double value, _) -> new NumConst(value);
                case MolangExpr.Str(String value, _) -> new ValueConst(new MolangValue.Str(value));
                case MolangExpr.Ident(String name, _) -> switch (name) {
                    case "continue" -> new Jump(MolangState.Continue.INSTANCE, "continue");
                    case "break" -> new Jump(MolangState.Break.INSTANCE, "break");
                    case "loop" -> new ErrorNode("'loop' must be called");
                    case "for_each", "this" -> new ErrorNode(
                        "'" + name + "' expressions are not supported"
                    );
                    default -> resolve(List.of(name), null);
                };
                case MolangExpr.Access access -> {
                    List<String> path = path(access);
                    yield path != null
                        ? resolve(path, null)
                        : new ErrorNode(
                            "Cannot access '" + access.field() + "' on " + access.lhs(),
                            compile(access.lhs())
                        );
                }
                case MolangExpr.Unary(MolangExpr.Unary.Op op, MolangExpr rhs, _) -> {
                    Node r = compile(rhs);
                    yield fold(
                        switch (op) {
                            case NEGATE -> new Negate(r);
                            case PLUS -> new Plus(r);
                            case NOT -> new Not(r);
                        },
                        r
                    );
                }
                case MolangExpr.Binary binary -> binary(binary);
                case MolangExpr.Ternary(
                    MolangExpr cond,
                    MolangExpr then,
                    MolangExpr otherwise,
                    _
                ) -> {
                    Node k = compile(cond), t = compile(then), o = compile(otherwise);
                    if (optimize && k instanceof NumConst constant)
                        yield constant.value != 0.0 ? t : o;
                    yield t.numeric() && o.numeric()
                        ? new NumTernary(k, t, o)
                        : new Ternary(k, t, o);
                }
                case MolangExpr.Block(List<MolangExpr> exprs, _) -> {
                    if (exprs.isEmpty()) yield new NumConst(0.0);
                    var statements = new ArrayList<Node>(exprs.size());
                    for (MolangExpr statement : exprs) {
                        Node node = compile(statement);
                        statements.add(node);
                        // Statements after a return never run
                        if (optimize && node instanceof Return) break;
                    }
                    yield statements.size() == 1
                        ? statements.getFirst()
                        : new Block(statements.toArray(Node[]::new));
                }
                case MolangExpr.Call call -> call(call);
                case MolangExpr.Assign(MolangExpr.Access target, MolangExpr value, _) -> {
                    if (target.lhs() instanceof MolangExpr.Ident(String scope, _)) {
                        switch (scope) {
                            case "variable", "v" -> {
                                var host = environment.variables;
                                String name = MolangExpr.normalizeName(target.field());
                                if (host != null
                                    && (host.entries().containsKey(name)
                                        || host.fallback() != null))
                                    yield ErrorNode.zero(
                                        "Cannot assign to " + target + ", which the host provides"
                                    );
                                variablesWritten.add("variable." + name);
                                yield new VariableAssign(
                                    false,
                                    symbol(target.field()),
                                    compile(value)
                                );
                            }
                            case "temp", "t" -> {
                                usesTemps = true;
                                yield new VariableAssign(
                                    true,
                                    symbol(target.field()),
                                    compile(value)
                                );
                            }
                            default -> {}
                        }
                    }
                    yield ErrorNode.zero(
                        "Cannot assign to " + target + ", only to variable and temp"
                    );
                }
                case MolangExpr.Return(MolangExpr value, _) -> new Return(compile(value));
            };
        }

        private Node call(MolangExpr.Call call) {
            if (call.lhs() instanceof MolangExpr.Ident(String name, _) && name.equals("loop")) {
                Node[] args = call.args().stream().map(this::compile).toArray(Node[]::new);
                boolean lastArgIsBlock = !call.args().isEmpty()
                    && call.args().getLast() instanceof MolangExpr.Block;
                return new Loop(args, lastArgIsBlock);
            }
            List<String> path = path(call.lhs());
            if (path == null)
                return new ErrorNode("Cannot call " + call.lhs(), compile(call.lhs()));
            return resolve(path, call.args());
        }

        /// Compiles a read of a name (`args` null), or a call of it.
        private Node resolve(List<String> path, @Nullable List<MolangExpr> args) {
            String written = String.join(".", path);
            String head = path.getFirst();
            switch (head) {
                case "variable", "v", "temp", "t" -> {
                    var host = head.startsWith("v") ? environment.variables : null;
                    if (host != null && path.size() >= 2) {
                        Entry bound = host.entries().get(path.get(1));
                        if (bound != null)
                            return walk(bound, "variable." + path.get(1), 2, path, args);
                        if (host.fallback() != null)
                            return dynamic(host.fallback(), "variable", 1, path, args);
                    }
                    if (path.size() != 2)
                        return new ErrorNode("'" + written + "' is not a variable");
                    if (args != null)
                        return new ErrorNode("Cannot call variable '" + written + "'");
                    boolean temp = head.startsWith("t");
                    if (temp) usesTemps = true;
                    else variablesRead.add("variable." + path.get(1));
                    return new Variable(temp, symbol(path.get(1)));
                }
                case "math", "m" -> {
                    if (path.size() != 2)
                        return new ErrorNode("'" + written + "' is not a math function");
                    Integer function = MATH_FUNCTIONS.get(path.get(1));
                    if (function == null) {
                        unresolved.add("math." + path.get(1));
                        return args == null
                            ? new ValueConst(MolangValue.NULL)
                            : new ErrorNode("Unknown function " + written);
                    }
                    Node[] compiled = compileArgs(args);
                    int arity = function / 100;
                    if (compiled.length != arity) return new Arity(written, compiled, arity);
                    Node node = switch (arity) {
                        case 0 -> new NumConst(Math.PI);
                        case 1 -> new Math1(written, compiled, function);
                        case 2 -> new Math2(written, compiled, function);
                        default -> new Math3(written, compiled, function);
                    };
                    if (!IMPURE_MATH.contains(path.get(1))) return fold(node, compiled);
                    random = true;
                    return node;
                }
                default -> {}
            }

            Entry entry = environment.root.get(head);
            if (entry != null) return walk(entry, canonical(entry, head), 1, path, args);
            return unresolved(written, args);
        }

        /// Compiles the name `path`, whose first `index` names resolved to `entry`, whose canonical
        /// name (with aliases replaced, eg `query.x` for `q.x`) is `canonical`.
        private Node walk(
            Entry entry,
            String canonical,
            int index,
            List<String> path,
            @Nullable List<MolangExpr> args
        ) {
            String written = String.join(".", path);
            for (int i = index;; i++) {
                if (entry instanceof Entry.Dynamic<?>(var resolver))
                    return dynamic(resolver, canonical, i, path, args);
                if (i == path.size()) {
                    if (!(entry instanceof Entry.Names || isPure(entry))) hostNames.add(canonical);
                    return bind(entry, written, args);
                }
                if (!(entry instanceof Entry.Names(var ignored, var entries, var fallback)))
                    return new ErrorNode(
                        "'"
                            + String.join(".", path.subList(0, i))
                            + "' has no field '"
                            + path.get(i)
                            + "'"
                    );
                Entry next = entries.get(path.get(i));
                if (next == null)
                    return fallback != null
                        ? dynamic(fallback, canonical, i, path, args)
                        : unresolved(
                            canonical + "." + String.join(".", path.subList(i, path.size())),
                            args
                        );
                canonical = next instanceof Entry.Names(var nextPath, var e, var f)
                    ? nextPath
                    : canonical + "." + path.get(i);
                entry = next;
            }
        }

        /// A call of a dynamic resolver with the names of `path` from `index` on, where the resolver
        /// answers the names under `canonical`.
        private Node dynamic(
            MolangEnvironment.DynamicResolver<?> resolver,
            String canonical,
            int index,
            List<String> path,
            @Nullable List<MolangExpr> args
        ) {
            String rest = String.join(".", path.subList(index, path.size()));
            dynamicNames.add(rest.isEmpty() ? canonical : canonical + "." + rest);
            return new ValueCall(String.join(".", path), compileArgs(args), resolver, rest);
        }

        /// The canonical name of a top level entry: a namespace's own path (so an alias like `q` is
        /// `query`), or the name it was reached by.
        private static String canonical(Entry entry, String name) {
            return entry instanceof Entry.Names(var path, var e, var f) ? path : name;
        }

        private static boolean isPure(Entry entry) {
            return entry instanceof Entry.Pure0
                || entry instanceof Entry.Pure1
                || entry instanceof Entry.Pure2
                || entry instanceof Entry.Pure3;
        }

        /// A name the environment does not have, by its canonical name.
        private Node unresolved(String name, @Nullable List<MolangExpr> args) {
            unresolved.add(name);
            return args == null
                ? new ValueConst(MolangValue.NULL)
                : new ErrorNode("Unknown function " + name);
        }

        private Node bind(Entry entry, String name, @Nullable List<MolangExpr> argExprs) {
            Node[] args = compileArgs(argExprs);
            return switch (entry) {
                case Entry.Names ignored -> new ErrorNode("'" + name + "' is not a value", args);
                case Entry.OptionalNumber<?>(
                    var fn
                ) -> args.length == 0
                    ? new OptionalNumberQuery(name, fn)
                    : new ErrorNode("Expected 0 arguments for " + name, args);
                case Entry.Pure0(
                    var fn
                ) -> args.length == 0 ? fold(new PureCall0(name, fn)) : new Arity(name, args, 0);
                case Entry.Number<?>(
                    var fn
                ) -> args.length == 0 ? new NumberQuery(name, fn) : new Arity(name, args, 0);
                case Entry.Bool<?>(
                    var fn
                ) -> args.length == 0 ? new BoolQuery(name, fn) : new Arity(name, args, 0);
                case Entry.Text<?>(
                    var fn
                ) -> args.length == 0
                    ? new TextQuery(name, fn)
                    : new ErrorNode("Expected 0 arguments for " + name, args);
                case Entry.Fn1<?>(
                    var fn
                ) -> args.length == 1 ? new HostCall1(name, args, fn) : new Arity(name, args, 1);
                case Entry.Fn2<?>(
                    var fn
                ) -> args.length == 2 ? new HostCall2(name, args, fn) : new Arity(name, args, 2);
                case Entry.Fn3<?>(
                    var fn
                ) -> args.length == 3 ? new HostCall3(name, args, fn) : new Arity(name, args, 3);
                case Entry.FnString<?>(
                    var fn
                ) -> args.length == 1
                    ? new HostCallString(name, args, fn)
                    : new Arity(name, args, 1);
                case Entry.FnVariadic<?>(var fn) -> new HostCallVariadic(name, args, fn);
                case Entry.FnValue<?>(var fn) -> new ValueCall(name, args, fn, name);
                case Entry.Pure1(
                    var fn
                ) -> args.length == 1
                    ? fold(new PureCall1(name, args, fn), args)
                    : new Arity(name, args, 1);
                case Entry.Pure2(
                    var fn
                ) -> args.length == 2
                    ? fold(new PureCall2(name, args, fn), args)
                    : new Arity(name, args, 2);
                case Entry.Pure3(
                    var fn
                ) -> args.length == 3
                    ? fold(new PureCall3(name, args, fn), args)
                    : new Arity(name, args, 3);
                case Entry.Dynamic<?> ignored -> throw new IllegalStateException(
                    "resolved before binding"
                );
            };
        }

        private Node[] compileArgs(@Nullable List<MolangExpr> args) {
            return args == null
                ? new Node[0]
                : args.stream().map(this::compile).toArray(Node[]::new);
        }

        private Node binary(MolangExpr.Binary binary) {
            Node l = compile(binary.lhs()), r = compile(binary.rhs());
            var op = binary.op();
            if (op == MolangExpr.Binary.Op.NULL_COALESCE) {
                if (!optimize) return new Coalesce(l, r);
                // A number or constant is never null
                if (l.numeric()
                    || l instanceof ValueConst k && !(k.value instanceof MolangValue.Null))
                    return l;
                return l instanceof ValueConst ? r : new Coalesce(l, r);
            }
            Node node = switch (op) {
                // IEEE addition and multiplication commute, so a constant can move to the right.
                case PLUS -> optimize && r instanceof NumConst k
                    ? new AddConst(l, k.value)
                    : optimize && l instanceof NumConst k
                        ? new AddConst(r, k.value)
                        : new Add(l, r);
                case MINUS -> optimize && r instanceof NumConst k
                    ? new SubConst(l, k.value)
                    : optimize && l instanceof NumConst k
                        ? new ConstSub(k.value, r)
                        : new Sub(l, r);
                case MUL -> optimize && r instanceof NumConst k
                    ? new MulConst(l, k.value)
                    : optimize && l instanceof NumConst k
                        ? new MulConst(r, k.value)
                        : new Mul(l, r);
                case DIV -> optimize && r instanceof NumConst k && k.value != 0.0
                    ? new DivConst(l, k.value)
                    : new Div(l, r);
                case EQ, NEQ -> l.numeric() && r.numeric()
                    ? new Compare(op, l, r)
                    : new Equals(op == MolangExpr.Binary.Op.EQ, l, r);
                case GTE, GT, LTE, LT -> new Compare(op, l, r);
                case AND, OR -> new Logic(op == MolangExpr.Binary.Op.AND, l, r);
                case NULL_COALESCE -> throw new IllegalStateException("unreachable");
            };
            return fold(node, l, r);
        }

        /// Replaces a node whose operands are all constants with its value, unless evaluating it
        /// reports an error (eg a division by zero), which must happen at runtime instead.
        private Node fold(Node node, Node... operands) {
            if (!optimize || node instanceof NumConst) return node;
            for (Node operand : operands)
                if (!(operand instanceof NumConst
                    || operand instanceof ValueConst k && k.value instanceof MolangValue.Str))
                    return node;
            if (scratch == null) scratch = new MolangState();
            scratch.begin(null);
            MolangValue value = node.value(scratch, null);
            if (!scratch.errors.isEmpty() || !(value instanceof MolangValue.Num(double number)))
                return node;
            return new NumConst(number);
        }

        /// The names of a chain of field accesses from a name, eg `q.r.yaw` for `q.r.yaw`, or null if
        /// the chain starts from anything else.
        private static @Nullable List<String> path(MolangExpr expr) {
            var names = new ArrayList<String>();
            while (expr instanceof MolangExpr.Access(MolangExpr lhs, String field, _)) {
                names.addFirst(MolangExpr.normalizeName(field));
                expr = lhs;
            }
            if (!(expr instanceof MolangExpr.Ident(String name, _))) return null;
            switch (name) {
                case "continue", "break", "loop", "for_each", "this" -> {
                    return null;
                }
                default -> names.addFirst(MolangExpr.normalizeName(name));
            }
            return names;
        }

        private static Symbol symbol(String name) {
            return Symbols.intern(name);
        }
    }
}
