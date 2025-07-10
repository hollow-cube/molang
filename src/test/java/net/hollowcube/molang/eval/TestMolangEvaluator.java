package net.hollowcube.molang.eval;

import net.hollowcube.molang.MolangParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    private static Stream<Arguments> inputPairs() {
        return Stream.of(
                Arguments.of("basic number",
                        "1", "1.0"),
                Arguments.of("basic arithmetic",
                        "1+4", "5.0"),
                Arguments.of("basic arithmetic 2",
                        "(4+3)*2", "14.0"),
                Arguments.of("basic math",
                        "2*math.pi", "6.283185307179586")
        );
    }

}
