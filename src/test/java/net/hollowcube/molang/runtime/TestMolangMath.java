package net.hollowcube.molang.runtime;

import net.hollowcube.molang.MolangParser;
import net.hollowcube.molang.eval.MolangEvaluator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMolangMath {

    @MethodSource("cases")
    @ParameterizedTest(name = "{0}")
    public void testMatchesMolangJs(String input, double expected) {
        var evaluator = new MolangEvaluator(Map.of());
        var actual = evaluator.eval(new MolangParser(input).parse());

        assertEquals(List.of(), evaluator.getErrors());
        assertEquals(expected, actual, 1e-9);
    }

    // Expected values are the results of evaluating the same expression with Blockbench's molangjs (1.7.0).
    public static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("math.lerp(2, 10, 0.25)", 4),
                Arguments.of("math.lerp(10, 2, 0.25)", 8),
                Arguments.of("math.lerp(2, 10, 1.5)", 14),
                Arguments.of("math.lerp(2, 10, -0.5)", -2),

                Arguments.of("math.inverse_lerp(2, 10, 4)", 0.25),
                Arguments.of("math.inverse_lerp(10, 2, 2)", 1),
                Arguments.of("math.inverse_lerp(2, 10, 12)", 1.25),
                Arguments.of("math.inverse_lerp(2, 10, -4)", -0.75)
        );
    }

}
