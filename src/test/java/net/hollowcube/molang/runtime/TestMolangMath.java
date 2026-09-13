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
            Arguments.of("math.inverse_lerp(2, 10, -4)", -0.75),
            Arguments.of("math.lerprotate(350, 10, 0.25)", 355),
            Arguments.of("math.lerprotate(10, 350, 0.25)", 355),
            Arguments.of("math.lerprotate(90, 0, 0)", 0),
            Arguments.of("math.lerprotate(0, 90, 0.5)", 45),
            Arguments.of("math.lerprotate(-170, 170, 0.5)", 180),
            Arguments.of("math.lerprotate(720, 45, 0.5)", 22.5),
            Arguments.of("math.lerprotate(30, 300, 1.5)", 75),
            Arguments.of("math.lerprotate(0, 180, 0.5)", 90),
            Arguments.of("math.lerprotate(0, 181, 0.5)", 270.5),
            Arguments.of("math.sign(0)", 0),
                Arguments.of("math.sign(2.5)", 1),
                Arguments.of("math.sign(-0.5)", -1),

                Arguments.of("math.ease_in_out_back(2, 10, 0)", 2),
                Arguments.of("math.ease_in_out_back(2, 10, 0.1)", 1.699851584),
                Arguments.of("math.ease_in_out_back(2, 10, 0.25)", 1.20254525),
                Arguments.of("math.ease_in_out_back(2, 10, 0.5)", 6),
                Arguments.of("math.ease_in_out_back(2, 10, 0.75)", 10.79745475),
                Arguments.of("math.ease_in_out_back(2, 10, 0.9)", 10.300148415999999),
                Arguments.of("math.ease_in_out_back(2, 10, 1)", 10),

                Arguments.of("math.ease_out_cubic(2, 10, 0)", 2),
                Arguments.of("math.ease_out_cubic(2, 10, 0.1)", 4.167999999999999),
                Arguments.of("math.ease_out_cubic(2, 10, 0.5)", 9),
                Arguments.of("math.ease_out_cubic(2, 10, 0.75)", 9.875),
                Arguments.of("math.ease_out_cubic(2, 10, 1)", 10),

                Arguments.of("math.ease_in_back(2, 10, 0)", 2),
                Arguments.of("math.ease_in_back(2, 10, 0.25)", 1.4869075),
                Arguments.of("math.ease_in_back(2, 10, 0.5)", 1.2984199999999997),
                Arguments.of("math.ease_in_back(2, 10, 0.9)", 6.729376159999999),
                Arguments.of("math.ease_in_back(2, 10, 1)", 9.999999999999998),
                Arguments.of("math.ease_out_back(2, 10, 0)", 2.0000000000000018),
                Arguments.of("math.ease_out_back(2, 10, 0.1)", 5.270623840000001),
                Arguments.of("math.ease_out_back(2, 10, 0.5)", 10.70158),
                Arguments.of("math.ease_out_back(2, 10, 0.75)", 10.5130925),
                Arguments.of("math.ease_out_back(2, 10, 1)", 10)
        );
    }

}
