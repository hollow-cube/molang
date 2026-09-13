package net.hollowcube.molang;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestMolangParser {

    @MethodSource("inputPairs")
    @ParameterizedTest(name = "{0}")
    public void testInputPairs(String name, String input, String expected) {
        var expr = new MolangParser(input).parse();
        var actual = MolangPrinter.print(expr);

        assertEquals(expected, actual);
    }

    private static Stream<Arguments> inputPairs() {
        return Stream.of(
            Arguments.of("basic number", "1", "1.0"),
            Arguments.of("basic ref", "abc", "abc"),
            Arguments.of("basic add", "1 + 2", "(+ 1.0 2.0)"),
            Arguments.of("nested add", "1 + 2 + 3", "(+ (+ 1.0 2.0) 3.0)"),
            Arguments.of("negate simple", "-1", "(- 1.0)"),
            Arguments.of("negate nested", "---1", "(- (- (- 1.0)))"),
            Arguments.of("negate precedence", "-2 + 1", "(+ (- 2.0) 1.0)"),
            Arguments.of("basic access", "a.b", "(. a b)"),
            Arguments.of("access/add precedence", "a.b + 1", "(+ (. a b) 1.0)"),
            Arguments.of("null coalesce precedence", "a.b ?? 1", "(?? (. a b) 1.0)"),
            Arguments.of(
                "null coalesce precedence 2",
                "1 + 2 ?? 1",
                "(?? (+ 1.0 2.0) 1.0)"
            ), //todo is this correct? should it be (+ 1.0 (?? 2.0 1.0))?
            Arguments.of("assignment", "v.x = 1", "(= (. v x) 1.0)"),
            Arguments.of(
                "assignment takes the whole rhs",
                "v.x = 1 ? 2 : 3",
                "(= (. v x) (? 1.0 2.0 3.0))"
            ),
            Arguments.of("assignment vs equality", "v.x == 1", "(== (. v x) 1.0)"),
            Arguments.of(
                "assignment in ternary branch",
                "0 ? v.x = 1 : 2",
                "(? 0.0 (= (. v x) 1.0) 2.0)"
            ),
            Arguments.of("statements", "v.x = 1; v.x", "{ (= (. v x) 1.0); (. v x) }"),
            Arguments.of("trailing semicolon", "1;", "1.0"),
            Arguments.of("blank statements", "\n1;\n\n;2;\n", "{ 1.0; 2.0 }"),
            Arguments.of("block without final semicolon", "{1; 2}", "{ 1.0; 2.0 }"),
            Arguments.of("empty block", "loop(2, {})", "(? loop 2.0 { })"),
            Arguments.of("return", "return 1 + 2", "(return (+ 1.0 2.0))"),
            Arguments.of("bare return", "return;", "(return 0.0)"),
            Arguments.of("return in block", "{return;}", "{ (return 0.0) }"),
            Arguments.of(
                "return statement",
                "v.x = 1; return v.x",
                "{ (= (. v x) 1.0); (return (. v x)) }"
            ),
            Arguments.of("normalize case 1", "q.is_alive()", "(. q is_alive)"),
            Arguments.of("empty call operand", "q.is_alive() + 1", "(+ (. q is_alive) 1.0)"),
            Arguments.of("trailing whitespace", "1 + 2 \n", "(+ 1.0 2.0)"),
            Arguments.of("normalize case 2", "q.is_alive", "(. q is_alive)"),
            Arguments.of("single ternary simple", "1 ? 2 : 3", "(? 1.0 2.0 3.0)"),
            Arguments.of("nested ternary", "1 ? 2 : 3 ? 4 : 5", "(? 1.0 2.0 (? 3.0 4.0 5.0))"),
            Arguments.of("ternary add precedence", "1 ? 2 + 3 : 4", "(? 1.0 (+ 2.0 3.0) 4.0)"),
            Arguments.of("loop call", "loop(10, {})", "(? loop 10.0 { })"),
            Arguments.of("lt eq precedence 1", "1 < 2 == 3", "(== (< 1.0 2.0) 3.0)"),
            Arguments.of("lt eq precedence 2", "1 == 2 < 3", "(== 1.0 (< 2.0 3.0))")
        );
    }

    @MethodSource("invalidInputs")
    @ParameterizedTest(name = "[{0}]")
    public void testInvalidInputs(String input) {
        assertThrows(IllegalStateException.class, () -> new MolangParser(input).parse());
    }

    private static Stream<String> invalidInputs() {
        return Stream.of(
            "1 2",
            "a.b c",
            "(1))",
            "1 ? 2 : 3 4",
            "{1;} 2",
            "q.is_alive() 1",
            "f(",
            "a = 1",
            "1 + v.x = 2",
            "v.x =",
            "v.x = = 1",
            "",
            ";",
            "1; 2 3",
            "{1; 2",
            "1 }"
        );
    }
}
