package net.hollowcube.molang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// What expressions evaluate to.
public class TestMolangEvaluator {
    record Times(double animTime, double lifeTime) {}

    static final MolangEnvironment<Times> TIMES = MolangEnvironment.<Times>builder()
        .query(q -> q.number("anim_time", Times::animTime).number("life_time", Times::lifeTime))
        .build();

    @MethodSource("inputPairs")
    @ParameterizedTest(name = "{0}")
    public void testInputPairs(String name, String input, String expected) {
        assertEquals(expected, String.valueOf(eval(new MolangState(), input)));
    }

    @Test
    public void testAssignment() {
        var state = new MolangState();

        assertEquals(0, eval(state, "v.x = 5"));
        assertEquals(0, eval(state, "variable.y = v.x * 2"));
        assertEquals(0, eval(state, "0 ? v.x = 1 : v.z = 3"));
        assertEquals(5, eval(state, "variable.x"));
        assertEquals(10, eval(state, "v.y"));
        assertEquals(3, eval(state, "v.z"));
        assertEquals(new MolangValue.Num(5), state.getVariable("x"));
    }

    @Test
    public void testTempAssignmentIsScopedToOneEvaluation() {
        var state = new MolangState();

        assertEquals(0, eval(state, "t.x = 5"));
        assertEquals(0, eval(state, "temp.y = 5"));
        assertEquals(7, eval(state, "(t.x ?? 7) + (temp.y ?? 0)"));
    }

    @Test
    public void testAssignmentToAnythingButVariables() {
        var state = new MolangState();
        for (String input : List.of("math.pi = 3", "q.x = 3", "c.x = 3")) {
            assertEquals(0, compile(MolangEnvironment.empty(), input).eval(state), input);
            assertEquals(1, state.getErrors().size(), () -> input + ": " + state.getErrors());
        }
    }

    @Test
    public void testHostVariables() {
        var state = new MolangState();
        state.setVariable("Speed", 2);
        state.setVariable("name", new MolangValue.Str("Pig"));
        assertEquals(4, eval(state, "v.speed * 2"));
        assertEquals(1, eval(state, "v.name == 'Pig'"));
        state.clearVariables();
        assertEquals(MolangValue.NULL, state.getVariable("speed"));
    }

    @MethodSource("scripts")
    @ParameterizedTest(name = "{0}")
    public void testScripts(String input, double expected) {
        var state = new MolangState();
        var actual = compile(TIMES, input).eval(state, new Times(0.3, 0.7));
        assertEquals(List.of(), state.getErrors());
        assertEquals(expected, actual, 1e-9);
    }

    // Expected values are the results of evaluating the same script with Blockbench's molangjs (1.7.0),
    // with q.anim_time = 0.3 and q.life_time = 0.7.
    static Stream<Arguments> scripts() {
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
            Arguments.of("67 // 21", 3),
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
        var state = new MolangState();
        assertEquals(2, eval(state, "v.a = 1; return v.a + 1; v.a = 9"));
        assertEquals(1, eval(state, "v.a"));
    }

    @Test
    public void testStrings() {
        var environment = MolangEnvironment.<String>builder()
            .query(q -> q.stringFunction("is_named", (name, text) -> name.equals(text) ? 1 : 0))
            .build();
        var state = new MolangState();

        assertEquals(1, eval(state, "'Pig' == 'Pig'"));
        assertEquals(0, eval(state, "'Pig' == 'pig'"));
        assertEquals(1, eval(state, "'Pig' != 'pig'"));
        assertEquals(1, eval(state, "'' == ''"));
        assertEquals(1, compile(environment, "q.is_named('Bob')").eval(state, "Bob"));
        assertEquals(0, compile(environment, "q.is_named('bob')").eval(state, "Bob"));
        assertEquals(1, eval(state, "v.name = 'Moo'; v.name == 'Moo'"));
        assertEquals(new MolangValue.Str("Moo"), state.getVariable("name"));
    }

    @Test
    public void testVariablesAreCaseInsensitive() {
        var state = new MolangState();
        eval(state, "V.Foo = 3");
        assertEquals(new MolangValue.Num(3), state.getVariable("FOO"));
        assertEquals(new MolangValue.Num(3), state.getVariable("foo"));
    }

    @MethodSource("contentErrors")
    @ParameterizedTest(name = "[{0}]")
    public void testContentErrorEvaluatesToZero(String input, double expected) {
        var state = new MolangState();
        var actual = compile(MolangEnvironment.empty(), input).eval(state);

        assertEquals(1, state.getErrors().size(), () -> state.getErrors().toString());
        assertEquals(expected, actual);
    }

    static Stream<Arguments> contentErrors() {
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

    static Stream<Arguments> inputPairs() {
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

    static <T> MolangProgram<T> compile(MolangEnvironment<T> environment, String input) {
        return environment.compile(input);
    }

    /// Evaluates an expression that must report no content errors.
    static double eval(MolangState state, String input) {
        var actual = compile(MolangEnvironment.empty(), input).eval(state);
        assertEquals(List.of(), state.getErrors(), input);
        return actual;
    }
}
