package net.hollowcube.molang;

import net.hollowcube.molang.eval.MolangEvaluator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class TestMolangOptimizer {

    @MethodSource("inputPairs")
    @ParameterizedTest(name = "{0}")
    public void testInputPairs(String name, String input, String expected) {
        var expr = new MolangParser(input).parse();
        var optimized = MolangOptimizer.optimizeAst(expr);
        var actual = MolangPrinter.print(optimized);

        assertEquals(expected, actual);
    }

    @MethodSource("net.hollowcube.molang.runtime.TestMolangMath#cases")
    @ParameterizedTest(name = "{0}")
    public void testFoldedMathMatchesEvaluation(String input, double ignored) {
        var expr = new MolangParser(input).parse();
        var optimized = MolangOptimizer.optimizeAst(expr);

        var folded = assertInstanceOf(MolangExpr.Num.class, optimized);
        assertEquals(new MolangEvaluator(Map.of()).eval(expr), folded.value());
    }

    private static Stream<Arguments> inputPairs() {
        return Stream.of(
                Arguments.of("basic number",
                        "1", "1.0"),
                Arguments.of("basic arithmetic",
                        "4*(2+3)", "20.0"),
                Arguments.of("basic arithmetic 2",
                        "-4", "-4.0"),
                Arguments.of("math func",
                        "math.lerp(0, 10, 0.5)", "5.0"),
                Arguments.of("invalid math func",
                        "math.lerp(0, 10)", "(? (. math lerp) 0.0 10.0)"),
                Arguments.of("invalid query func",
                        "q.anim_time + 5", "(+ (. q anim_time) 5.0)")
        );
    }

}
