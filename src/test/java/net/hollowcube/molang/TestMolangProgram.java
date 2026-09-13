package net.hollowcube.molang;

import net.hollowcube.molang.runtime.MolangContentException;
import net.hollowcube.molang.runtime.MolangMath;
import net.hollowcube.molang.runtime.TestMolangMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Compiling with optimizations (constant folding and nodes specialized for constant operands) must
/// not change what a program does: the optimized and unoptimized programs must agree on value, number
/// of content errors and variables afterwards. Absolute results are pinned by [TestMolangEvaluator],
/// [TestMolangEnvironment] and [TestMolangMath].
public class TestMolangProgram {
    record Host(double animTime, String name) {}

    static final MolangEnvironment<Host> HOST = MolangEnvironment.<Host>builder()
        .query(
            q -> q.number("anim_time", Host::animTime)
                .string("name", Host::name)
                .variadic("count", (host, args) -> args.length)
                .number("fail", host -> {
                    throw new IllegalStateException("host failure");
                })
                .function("domain", (host, a) -> {
                    if (a < 0) throw new MolangContentException("domain: negative");
                    return Math.sqrt(a);
                })
                .pure("twice", a -> a * 2)
                .stringFunction("is_named", (host, name) -> host.name().equals(name) ? 1 : 0)
                .value("echo", (host, args) -> args.isEmpty() ? MolangValue.NULL : args.getFirst())
                .namespace("nested", nested -> nested.constant("x", 2))
                .dynamic(
                    "dyn",
                    (
                        host,
                        name,
                        args
                    ) -> name.equals("a") ? new MolangValue.Num(args.size()) : MolangValue.NULL
                )
        )
        .variables(v -> v.number("host", Host::animTime))
        .build();

    private static Stream<String> sources() {
        return Stream
            .of(
                TestMolangEvaluator.scripts().map(args -> (String) args.get()[0]),
                TestMolangEvaluator.contentErrors().map(args -> (String) args.get()[0]),
                TestMolangEvaluator.inputPairs().map(args -> (String) args.get()[1]),
                TestMolangMath.cases().map(args -> (String) args.get()[0]),
                TestMolangMath.domainErrors(),
                Stream.of(
                    "math.random(2.5, 2.5)",
                    "math.pi",
                    "math.pi()",
                    "math.sin",
                    "math.sin(1, 2)",
                    "math.sin('a')",
                    "math.sin(v.unset)",
                    "math.unknown",
                    "math.unknown(1)",
                    "1 / 0",
                    "'a' / 0",
                    "0 && 'a'",
                    "1 || 'a'",
                    "'a' ?? 3",
                    "math.unknown ?? 3",
                    "q.typo ?? 3",
                    "v.x = 'str'; v.x ?? 3",
                    "-+5",
                    "+'a'",
                    "break",
                    "loop",
                    "loop(1)",
                    "loop('a', {})",
                    "loop(2000, {v.i = (v.i ?? 0) + 1;}); v.i",
                    "v.i = 0; loop(10, {v.i = v.i + 1; v.i > 4 ? break : continue;}); v.i",
                    "q.anim_time * 90 + q.twice(3)",
                    "q.twice(q.anim_time)",
                    "q.count(1, 2, v.unset)",
                    "q.fail + 1",
                    "q.domain(-1) + q.domain(4)",
                    "q.name == 'Pig'",
                    "q.is_named('Pig') + q.is_named(1)",
                    "q.echo('x') == 'x'",
                    "q.nested.x * 3",
                    "q.nested",
                    "q.nested.x.y",
                    "q.dyn.a(1, 2) + q.dyn.b",
                    "q.anim_time(1)",
                    "v.x.y",
                    "v.host * 2 + (v.host = 1)",
                    "'a'.x",
                    "unknown(1)",
                    "return 'str'",
                    "'a' + (return 1)",
                    "{1; {2; 3}}"
                )
            )
            .flatMap(s -> s);
    }

    @MethodSource("sources")
    @ParameterizedTest(name = "[{0}]")
    public void testOptimizationsDoNotChangeResults(String source) {
        assertSame(List.of(parse(source)));
    }

    @Test
    public void testOptimizationsDoNotChangeStatefulSessions() {
        assertSame(
            Stream.of("v.x = 5", "variable.y = v.x * 2", "0 ? v.x = 1 : v.z = 3", "v.y")
                .map(TestMolangProgram::parse)
                .toList()
        );
        assertSame(
            Stream.of("t.x = 5", "(t.x ?? 7) + (temp.y ?? 0)")
                .map(TestMolangProgram::parse)
                .toList()
        );
    }

    @Test
    public void testOptimizationsDoNotChangeRandomExpressions() {
        // -Dmolang.fuzz.seed and -Dmolang.fuzz.iterations run a longer or different search.
        var random = new Random(Long.getLong("molang.fuzz.seed", 20260913L));
        for (int i = Integer.getInteger("molang.fuzz.iterations", 20_000); i > 0; i--) {
            MolangExpr expr = new ExprGenerator(random).expr(4);
            assertSame(List.of(expr, expr, new ExprGenerator(random).expr(3)));
        }
    }

    @Test
    public void testLogicShortCircuits() {
        var state = new MolangState();
        assertEquals(0, TestMolangEvaluator.eval(state, "v.a = 0; 0 && (v.a = 1); v.a"));
        assertEquals(0, TestMolangEvaluator.eval(state, "v.a = 0; 1 || (v.a = 1); v.a"));
        assertEquals(1, TestMolangEvaluator.eval(state, "v.a = 0; 1 && (v.a = 1); v.a"));
        assertEquals(1, TestMolangEvaluator.eval(state, "v.a = 0; 0 || (v.a = 1); v.a"));
        assertEquals(0, TestMolangEvaluator.eval(state, "0 && 'text'"));
    }

    @Test
    public void testUnaryPlus() {
        var state = new MolangState();
        assertEquals(5, TestMolangEvaluator.eval(state, "+5"));
        assertEquals(-5, TestMolangEvaluator.eval(state, "-+5"));
        assertEquals(6, TestMolangEvaluator.eval(state, "2 * +3"));
        assertEquals(0, MolangProgram.compile("+'text'").eval(state));
        assertEquals(1, state.getErrors().size());
    }

    @MethodSource("net.hollowcube.molang.runtime.TestMolangMath#cases")
    @ParameterizedTest(name = "{0}")
    public void testPureMathIsFolded(String input, double ignored) {
        var unoptimized = MolangProgram.compile(
            MolangEnvironment.empty(),
            parse(input),
            null,
            false
        );
        assertEquals(
            OptionalDouble.of(unoptimized.eval(new MolangState())),
            MolangProgram.compile(input).analysis().constant()
        );
    }

    @Test
    public void testFolding() {
        assertEquals(OptionalDouble.of(20.0), constant("4*(2+3)"));
        assertEquals(OptionalDouble.of(-4.0), constant("-4"));
        assertEquals(OptionalDouble.of(1.0), constant("'Pig' == 'Pig'"));
        assertEquals(OptionalDouble.of(0.0), constant("'Pig' == 'pig'"));
        assertEquals(OptionalDouble.of(3.0), constant("1 ? 3 : q.anim_time"));
        assertEquals(OptionalDouble.of(Math.PI * 2), constant("math.pi * 2"));
        assertEquals(OptionalDouble.of(1.0), constant("0 || math.max(1, 2)"));
        assertEquals(OptionalDouble.of(12.0), constant("q.twice(3) * q.nested.x"));
        assertEquals(OptionalDouble.of(3.0), constant("q.typo ?? 3"));
        // Not constant, or an error that must be reported when evaluated
        for (String source : List.of(
            "1 / (2 - 2)",
            "math.lerp(0, 10)",
            "math.sqrt(-1)",
            "'a' + 1",
            "q.anim_time + 5",
            "q.twice(q.anim_time)",
            "q.count(1)",
            "math.random(1, 2)",
            "v.x = 1 + 2",
            "return 1 + 2"
        ))
            assertEquals(OptionalDouble.empty(), constant(source), source);
    }

    @Test
    public void testEveryMathFunctionIsCompiled() {
        for (Method method : MolangMath.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())
                || !Modifier.isStatic(method.getModifiers()))
                continue;
            String name = method.getName().replaceAll("([A-Z])", "_$1").toLowerCase();
            Integer function = MolangProgram.MATH_FUNCTIONS.get(name);
            assertTrue(function != null, () -> "math." + name + " is not compiled");
            assertEquals(method.getParameterCount(), function / 100, () -> "arity of math." + name);
        }
    }

    private static MolangExpr parse(String source) {
        return new MolangParser(source).parse();
    }

    private static OptionalDouble constant(String source) {
        return HOST.compile(source).analysis().constant();
    }

    private static void assertSame(List<MolangExpr> exprs) {
        var host = new Host(0.35, "Pig");
        var unoptimized = new MolangState();
        var optimized = new MolangState();
        for (MolangExpr expr : exprs) {
            double expected = MolangProgram.compile(HOST, expr, null, false)
                .eval(unoptimized, host);
            double actual = MolangProgram.compile(HOST, expr, null, true).eval(optimized, host);
            assertEquals(
                Double.doubleToLongBits(expected),
                Double.doubleToLongBits(actual),
                () -> expr + ": unoptimized " + expected + ", optimized " + actual
            );
            assertEquals(
                unoptimized.getErrors().size(),
                optimized.getErrors().size(),
                () -> expr
                    + ": unoptimized errors "
                    + unoptimized.getErrors()
                    + ", optimized "
                    + optimized.getErrors()
            );
        }
        for (String name : Set.of("a", "b", "i", "x", "y", "z"))
            assertEquals(
                unoptimized.getVariable(name),
                optimized.getVariable(name),
                () -> exprs + ": v." + name
            );
    }

    /// Random, mostly valid expressions over everything the compiler supports except randomness.
    private record ExprGenerator(Random random) {
        private static final List<String> MATH = MolangProgram.MATH_FUNCTIONS.keySet()
            .stream()
            .filter(name -> !name.contains("random") && !name.contains("die_roll"))
            .sorted()
            .toList();
        private static final List<String> QUERIES = List.of(
            "anim_time",
            "name",
            "count",
            "fail",
            "domain",
            "twice",
            "is_named",
            "echo",
            "typo"
        );

        MolangExpr expr(int depth) {
            if (depth <= 0) return leaf();
            return switch (random.nextInt(15)) {
                case 0, 1 -> leaf();
                case 2 -> new MolangExpr.Unary(pick(MolangExpr.Unary.Op.values()), expr(depth - 1));
                case 3, 4, 5 -> new MolangExpr.Binary(
                    pick(MolangExpr.Binary.Op.values()),
                    expr(depth - 1),
                    expr(depth - 1)
                );
                case 6 -> new MolangExpr.Ternary(expr(depth - 1), expr(depth - 1), expr(depth - 1));
                case 7 -> new MolangExpr.Block(list(depth - 1, 3));
                case 8 -> mathCall(depth - 1);
                case 9 -> new MolangExpr.Call(
                    pick(
                        List.of(
                            access("q", pick(QUERIES)),
                            access(access("q", "dyn"), "a"),
                            access("v", "a"),
                            new MolangExpr.Ident("unknown")
                        )
                    ),
                    list(depth - 1, 3)
                );
                case 10 -> new MolangExpr.Call(
                    new MolangExpr.Ident("loop"),
                    random.nextInt(8) == 0
                        ? list(depth - 1, 3)
                        : List.of(
                            new MolangExpr.Num(random.nextInt(4)),
                            new MolangExpr.Block(list(depth - 1, 3))
                        )
                );
                case 11 -> new MolangExpr.Assign(
                    pick(
                        List.of(
                            access("v", "a"),
                            access("v", "b"),
                            access("t", "a"),
                            access("v", "host"),
                            access("q", "x"),
                            access("math", "pi")
                        )
                    ),
                    expr(depth - 1)
                );
                case 12 -> new MolangExpr.Return(expr(depth - 1));
                case 13 -> new MolangExpr.Str(pick(List.of("", "Pig", "pig")));
                default -> new MolangExpr.Access(
                    expr(depth - 1),
                    pick(List.of("a", "x", "anim_time"))
                );
            };
        }

        private MolangExpr leaf() {
            return switch (random.nextInt(12)) {
                case 0, 1, 2 -> new MolangExpr.Num(
                    pick(List.of(0.0, 1.0, -1.0, 2.5, 90.0, 0.35, 1e-3))
                );
                case 3 -> new MolangExpr.Str(pick(List.of("", "Pig", "pig")));
                case 4 -> new MolangExpr.Ident(
                    pick(List.of("break", "continue", "loop", "this", "q", "v", "math", "unknown"))
                );
                default -> pick(
                    List.of(
                        access("q", pick(QUERIES)),
                        access(access("q", "nested"), "x"),
                        access(access("q", "dyn"), pick(List.of("a", "b"))),
                        access("q", "nested"),
                        access("v", "a"),
                        access("v", "b"),
                        access("v", "host"),
                        access("variable", "a"),
                        access("t", "a"),
                        access("temp", "a"),
                        access("math", "pi"),
                        access("math", "sin"),
                        access("math", "unknown"),
                        access("unknown", "x")
                    )
                );
            };
        }

        private MolangExpr mathCall(int depth) {
            String name = pick(MATH);
            int arity = MolangProgram.MATH_FUNCTIONS.get(name) / 100;
            if (random.nextInt(10) == 0) arity = random.nextInt(4);
            List<MolangExpr> args = new ArrayList<>();
            for (int i = 0; i < arity; i++) args.add(expr(depth));
            return new MolangExpr.Call(access(random.nextInt(4) == 0 ? "m" : "math", name), args);
        }

        private List<MolangExpr> list(int depth, int max) {
            List<MolangExpr> exprs = new ArrayList<>();
            for (int i = random.nextInt(max + 1); i > 0; i--) exprs.add(expr(depth));
            return exprs;
        }

        private static MolangExpr.Access access(String holder, String field) {
            return access(new MolangExpr.Ident(holder), field);
        }

        private static MolangExpr.Access access(MolangExpr holder, String field) {
            return new MolangExpr.Access(holder, field);
        }

        private <T> T pick(T[] values) {
            return values[random.nextInt(values.length)];
        }

        private <T> T pick(List<T> values) {
            return values.get(random.nextInt(values.size()));
        }
    }
}
