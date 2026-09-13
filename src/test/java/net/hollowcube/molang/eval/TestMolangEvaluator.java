package net.hollowcube.molang.eval;

import net.hollowcube.molang.MolangParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static double eval(MolangEvaluator evaluator, String input) {
        var actual = evaluator.eval(new MolangParser(input).parse());
        assertEquals(List.of(), evaluator.getErrors());
        return actual;
    }

    @MethodSource("contentErrors")
    @ParameterizedTest(name = "{0}")
    public void testContentErrorEvaluatesToZero(String input, double expected) {
        var evaluator = new MolangEvaluator(Map.of());
        var actual = evaluator.eval(new MolangParser(input).parse());

        assertEquals(1, evaluator.getErrors().size(), () -> evaluator.getErrors().toString());
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> contentErrors() {
        return Stream.of(
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
