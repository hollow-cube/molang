package net.hollowcube.molang.eval;

import net.hollowcube.molang.MolangParser;
import net.hollowcube.molang.runtime.MolangMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestMolangEvaluator {

    @MethodSource("inputPairs")
    @ParameterizedTest(name = "{0}")
    public void testInputPairs(String name, String input, String expected) {
        var expr = new MolangParser(input).parse();
        var actual = new MolangEvaluator(Map.of()).eval(expr);

        assertEquals(expected, String.valueOf(actual));
    }

    @Test
    public void testAssignment() {
        var evaluator = new MolangEvaluator(Map.of());

        assertEquals(0, eval(evaluator, "v.x = 5"));
        assertEquals(0, eval(evaluator, "variable.y = v.x * 2"));
        assertEquals(0, eval(evaluator, "0 ? v.x = 1 : v.z = 3"));
        assertEquals(5, eval(evaluator, "variable.x"));
        assertEquals(10, eval(evaluator, "v.y"));
        assertEquals(3, eval(evaluator, "v.z"));
        assertEquals(new MolangValue.Num(5), evaluator.getVariable("x"));
    }

    @Test
    public void testTempAssignmentIsScopedToOneEvaluation() {
        var evaluator = new MolangEvaluator(Map.of());

        assertEquals(0, eval(evaluator, "t.x = 5"));
        assertEquals(0, eval(evaluator, "temp.y = 5"));
        assertEquals(7, eval(evaluator, "(t.x ?? 7) + (temp.y ?? 0)"));
    }

    @Test
    public void testAssignmentToReadOnlyHolder() {
        var evaluator = new MolangEvaluator(Map.of());
        assertEquals(0, evaluator.eval(new MolangParser("math.pi = 3").parse()));
        assertEquals(1, evaluator.getErrors().size(), () -> evaluator.getErrors().toString());
    }

    @MethodSource("scripts")
    @ParameterizedTest(name = "{0}")
    public void testScripts(String input, double expected) {
        var query = MolangValue.Holder.holder(
            Map.of("anim_time", new MolangValue.Num(0.3), "life_time", new MolangValue.Num(0.7))
        );
        var evaluator = new MolangEvaluator(Map.of("q", query, "query", query));
        assertEquals(expected, eval(evaluator, input), 1e-9);
    }

    // Expected values are the results of evaluating the same script with Blockbench's molangjs (1.7.0),
    // with q.anim_time = 0.3 and q.life_time = 0.7.
    private static Stream<Arguments> scripts() {
        return Stream.of(
            Arguments.of("v.a = 2; v.a * 5", 10),
            Arguments.of("v.a = 2; v.b = 4;", 0),
            Arguments.of("1; 2; 3", 3),
            Arguments.of("1;2;", 2),
            Arguments.of("v.i = 0; loop(3, {v.i = v.i + 1;}); v.i", 3),
            Arguments.of("{v.q = 4; v.q + 1}", 5),
            Arguments.of("loop(2, {}); 1", 1),
            Arguments.of("1 ? 5", 5),
            Arguments.of("0 ? 5", 0),
            Arguments.of("1 ? 2 + 3", 5),
            Arguments.of("0 + 1 ? 4", 4),
            // The C rule the Bedrock docs specify, where molangjs pairs the : with the outer ? (giving 0 and 4)
            Arguments.of("1 ? 0 ? 3 : 4", 4),
            Arguments.of("0 ? 1 ? 3 : 4", 0),
            Arguments.of("1 ? 0 ? 3 : 4 : 5", 4),
            Arguments.of("1 ? (0 ? 3 : 4)", 4),
            Arguments.of("1 ? math.max(0 ? 3 : 4, 1)", 4),
            Arguments.of("1 ? {0 ? 3 : 4}", 4),
            Arguments.of("1 ? 2 : 0 ? 4 : 5", 2),
            Arguments.of("0 ? 2 : 0 ? 4 : 5", 5),
            Arguments.of("0 ?? 2 ? 3 : 4", 0),
            Arguments.of("1 ? 2 : 3 ?? 9", 2),
            Arguments.of("v.z = 1 ? 2; v.z", 2),
            Arguments.of("Math.Lerp(0, 10, 0.5)", 5),
            Arguments.of("Q.Anim_Time * 10", 3),
            Arguments.of("V.Foo = 3; v.foo", 3),
            Arguments.of("Return 4; 5", 4),
            Arguments.of("v.i = 0; Loop(2, {V.I = V.I + 1;}); v.i", 2),
            Arguments.of("return 7; 8", 7),
            Arguments.of("return;", 0),
            Arguments.of("v.a = 1; return v.a + 1; v.a = 9", 2),
            Arguments.of("1 ? {return 3;} : 4; 9", 3),
            Arguments.of(
                "v.i = 0; loop(10, {v.i = v.i + 1; v.i == 3 ? {return v.i * 10;} : 0;}); 99",
                30
            ),
            Arguments.of(
                "t.turn = 15;\nreturn t.turn * math.cos(q.anim_time * 360);\n",
                -4.63525491562421
            ),
            Arguments.of(
                "v.progress = math.cos(q.life_time * 360 - 22.5) * 0.5 + 0.5;\nv.ease = v.progress < 0.5\n      ? 4 * v.progress * v.progress * v.progress\n      : 1 - math.pow(-2 * v.progress + 2, 3) / 2;\n\nreturn v.ease;",
                0.02153908114499394
            ),
            Arguments.of(
                "v.freq = 2;\nv.mag = 45;\nv.balloon = 2;\nv.offset = 0;\nv.time_offset = 0;\n\nmath.atan(v.balloon * math.sin((q.anim_time + v.time_offset) * 180 * v.freq)) / math.atan(v.balloon) * v.mag + v.offset",
                44.171966508167216
            )
        );
    }

    @Test
    public void testReturnSkipsLaterStatements() {
        var evaluator = new MolangEvaluator(Map.of());
        assertEquals(2, eval(evaluator, "v.a = 1; return v.a + 1; v.a = 9"));
        assertEquals(1, eval(evaluator, "v.a"));
    }

    @Test
    public void testStrings() {
        MolangValue.Function isNamed = args -> new MolangValue.Num(
            args.size() == 1
                && args.getFirst() instanceof MolangValue.Str(var name)
                && name.equals("Bob")
        );
        var evaluator = new MolangEvaluator(
            Map.of("q", MolangValue.Holder.holder(Map.of("is_named", isNamed)))
        );

        assertEquals(1, eval(evaluator, "'Pig' == 'Pig'"));
        assertEquals(0, eval(evaluator, "'Pig' == 'pig'"));
        assertEquals(1, eval(evaluator, "'Pig' != 'pig'"));
        assertEquals(1, eval(evaluator, "'' == ''"));
        assertEquals(1, eval(evaluator, "q.is_named('Bob')"));
        assertEquals(0, eval(evaluator, "q.is_named('bob')"));
        assertEquals(1, eval(evaluator, "v.name = 'Moo'; v.name == 'Moo'"));
        assertEquals(new MolangValue.Str("Moo"), evaluator.getVariable("name"));
    }

    @Test
    public void testHostNamesAreCaseInsensitive() {
        var query = MolangValue.Holder.holder(
            Map.of("Anim_Time", new MolangValue.Num(0.3), "isAlive", new MolangValue.Num(1))
        );
        var evaluator = new MolangEvaluator(Map.of("Q", query, "Query", query));

        assertEquals(0.3, eval(evaluator, "q.anim_time"));
        assertEquals(0.3, eval(evaluator, "Query.ANIM_TIME"));
        assertEquals(1, eval(evaluator, "q.isAlive"));
        assertEquals(1, eval(evaluator, "query.isalive"));
        assertEquals(new MolangValue.Num(0.3), query.get("ANIM_time"));
        assertEquals(
            new MolangValue.Num(5),
            MolangMath.MODULE.get("Lerp") instanceof MolangValue.Function lerp
                ? lerp.apply(
                    List.of(
                        new MolangValue.Num(0),
                        new MolangValue.Num(10),
                        new MolangValue.Num(0.5)
                    )
                )
                : MolangValue.NIL
        );
    }

    @Test
    public void testVariablesAreCaseInsensitive() {
        var evaluator = new MolangEvaluator(Map.of());
        eval(evaluator, "V.Foo = 3");
        assertEquals(new MolangValue.Num(3), evaluator.getVariable("FOO"));
        assertEquals(new MolangValue.Num(3), evaluator.getVariable("foo"));
    }

    @Test
    public void testNamesDifferingOnlyByCaseAreRejected() {
        var one = new MolangValue.Num(1);
        assertThrows(
            IllegalArgumentException.class,
            () -> MolangValue.Holder.holder(Map.of("a", one, "A", one))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MolangEvaluator(Map.of("q", one, "Q", one))
        );
    }

    private static double eval(MolangEvaluator evaluator, String input) {
        var actual = evaluator.eval(new MolangParser(input).parse());
        assertEquals(List.of(), evaluator.getErrors());
        return actual;
    }

    @MethodSource("contentErrors")
    @ParameterizedTest(name = "[{0}]")
    public void testContentErrorEvaluatesToZero(String input, double expected) {
        var evaluator = new MolangEvaluator(Map.of());
        var actual = evaluator.eval(new MolangParser(input).parse());

        assertEquals(1, evaluator.getErrors().size(), () -> evaluator.getErrors().toString());
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> contentErrors() {
        return Stream.of(
            Arguments.of("", 0),
            Arguments.of(";", 0),
            Arguments.of(" \n ;; \t", 0),
            Arguments.of("{}", 0),
            Arguments.of("'text' + 1", 1),
            Arguments.of("'text'", 0),
            Arguments.of("'text' == 1", 0),
            Arguments.of("math.asin(2)", 0),
            Arguments.of("math.asin(2) + 1", 1),
            Arguments.of("math.max(math.asin(2), 5)", 5),
            Arguments.of("math.max(math.mod(5, 0), -5)", 0),
            Arguments.of("math.asin(2) ?? 5", 0),
            Arguments.of("math.max(v.unset, 5)", 5),
            Arguments.of("math.abs(v.unset) + 1", 1)
        );
    }

    private static Stream<Arguments> inputPairs() {
        return Stream.of(
            Arguments.of("basic number", "1", "1.0"),
            Arguments.of("basic arithmetic", "1+4", "5.0"),
            Arguments.of("basic arithmetic 2", "(4+3)*2", "14.0"),
            Arguments.of("basic math", "2*math.pi", "6.283185307179586"),
            Arguments.of("simple and 1", "1 && 0", "0.0"),
            Arguments.of("simple and 2", "1 && 1", "1.0"),
            Arguments.of("simple and 3", "1 && 1 && 1 && 1 && 0", "0.0"),
            Arguments.of("simple or 1", "1 || 0", "1.0"),
            Arguments.of("simple or 2", "0 || 0", "0.0"),
            Arguments.of("simple or 3", "0 || 0 || 0 || 0 || 1", "1.0"),
            Arguments.of("simple not 1", "!1", "0.0"),
            Arguments.of("simple not 2", "!0", "1.0"),
            Arguments.of("ease_in_out_cubic 1", "m.ease_in_out_cubic(0, 1, 0.5)", "0.5"),
            Arguments.of("ease_in_out_cubic 2", "m.ease_in_out_cubic(0, 1, 0.75)", "0.9375")
        );
    }

}
