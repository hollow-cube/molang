package net.hollowcube.molang;

import org.junit.jupiter.api.Test;
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
            Arguments.of("unary plus", "+1", "(+ 1.0)"),
            Arguments.of("unary plus after operator", "2 * +3", "(* 2.0 (+ 3.0))"),
            Arguments.of("unary plus and minus", "-+1", "(- (+ 1.0))"),
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
            Arguments.of(
                "string keeps case",
                "V.Name == 'Minecraft:Pig'",
                "(== (. v name) 'Minecraft:Pig')"
            ),
            Arguments.of("empty string", "''", "''"),
            Arguments.of("string with spaces", "' a b '", "' a b '"),
            Arguments.of(
                "string argument",
                "q.is_item_equipped('Main_Hand')",
                "(? (. q is_item_equipped) 'Main_Hand')"
            ),
            Arguments.of("empty script", "", "{ }"),
            Arguments.of("whitespace and semicolon script", " \n ;; \t", "{ }"),
            Arguments.of("binary conditional", "a ? b", "(? a b 0.0)"),
            Arguments.of("binary conditional takes the rest", "a ? b + c", "(? a (+ b c) 0.0)"),
            Arguments.of("conditional in then branch", "a ? b ? c : d", "(? a (? b c d) 0.0)"),
            Arguments.of(
                "conditionals in both branches",
                "a ? b ? c : d : e ? f : g",
                "(? a (? b c d) (? e f g))"
            ),
            Arguments.of(
                "grouped conditional in then branch",
                "a ? (b ? c : d)",
                "(? a (? b c d) 0.0)"
            ),
            Arguments.of("conditional in else branch", "a ? b : c ? d", "(? a b (? c d 0.0))"),
            Arguments.of(
                "null coalesce binds looser than conditional",
                "a ?? b ? c : d",
                "(?? a (? b c d))"
            ),
            Arguments.of("null coalesce after conditional", "a ? b : c ?? d", "(?? (? a b c) d)"),
            Arguments.of(
                "null coalesce after binary conditional",
                "a ? b ?? c",
                "(?? (? a b 0.0) c)"
            ),
            Arguments.of("binary conditional assignment", "v.z = a ? b", "(= (. v z) (? a b 0.0))"),
            Arguments.of(
                "case-insensitive names",
                "Math.Lerp(V.Foo, 1, 2)",
                "(? (. math lerp) (. v foo) 1.0 2.0)"
            ),
            Arguments.of("case-insensitive return", "RETURN 1", "(return 1.0)"),
            Arguments.of("normalize case 1", "q.is_alive()", "(. q is_alive)"),
            Arguments.of("global zero argument call", "foo()", "(? foo )"),
            Arguments.of("grouped member zero argument call", "(q.foo)()", "(. q foo)"),
            Arguments.of("empty call operand", "q.is_alive() + 1", "(+ (. q is_alive) 1.0)"),
            Arguments.of("trailing whitespace", "1 + 2 \n", "(+ 1.0 2.0)"),
            Arguments.of("normalize case 2", "q.is_alive", "(. q is_alive)"),
            Arguments.of("single ternary simple", "1 ? 2 : 3", "(? 1.0 2.0 3.0)"),
            Arguments.of("nested ternary", "1 ? 2 : 3 ? 4 : 5", "(? 1.0 2.0 (? 3.0 4.0 5.0))"),
            Arguments.of("ternary add precedence", "1 ? 2 + 3 : 4", "(? 1.0 (+ 2.0 3.0) 4.0)"),
            Arguments.of("loop call", "loop(10, {})", "(? loop 10.0 { })"),
            Arguments.of("lt eq precedence 1", "1 < 2 == 3", "(== (< 1.0 2.0) 3.0)"),
            Arguments.of("lt eq precedence 2", "1 == 2 < 3", "(== 1.0 (< 2.0 3.0))"),
            Arguments.of("exponent", "3.27e-7", "3.27E-7"),
            Arguments.of("exponent upper case", "2E3", "2000.0"),
            Arguments.of("exponent plus", "1.5e+2 * q.x", "(* 150.0 (. q x))"),
            Arguments.of("exponent without fraction", "1e2", "100.0"),
            Arguments.of("exponent after dot", "1.e1", "10.0")
        );
    }

    @MethodSource("invalidInputs")
    @ParameterizedTest(name = "[{0}]")
    public void testInvalidInputs(String input) {
        assertThrows(MolangParseException.class, () -> new MolangParser(input).parse());
    }

    @Test
    public void testExpressionPositions() {
        var expr = (MolangExpr.Block) new MolangParser("v.a = -q.b;\n2 * math.sin(1) ? 'x'")
            .parse();
        var assign = (MolangExpr.Assign) expr.exprs().get(0);
        assertEquals(4, assign.position()); // =
        assertEquals(0, assign.target().position()); // v.a
        assertEquals(6, assign.value().position()); // -
        var ternary = (MolangExpr.Ternary) expr.exprs().get(1);
        assertEquals(28, ternary.position()); // ?
        var product = (MolangExpr.Binary) ternary.cond();
        assertEquals(14, product.position()); // *
        assertEquals(16, product.rhs().position()); // math.sin(1)
        assertEquals(30, ternary.thenExpr().position()); // 'x'
        assertEquals(-1, new MolangExpr.Num(1).position());
    }

    @Test
    public void testParseErrorsNameTheirLocation() {
        var error = assertThrows(
            MolangParseException.class,
            () -> new MolangParser("v.a = 1;\nv.b = 2 3;").parse()
        );
        assertEquals("v.a = 1;\nv.b = 2 3;", error.source());
        assertEquals(17, error.position());
        assertEquals(2, error.line());
        assertEquals(9, error.column());
        assertEquals("unexpected NUMBER after expression at line 2, column 9", error.getMessage());

        var end = assertThrows(MolangParseException.class, () -> new MolangParser("1 +").parse());
        assertEquals(3, end.position());
        assertEquals("unexpected end of input", end.reason());

        var assign = assertThrows(
            MolangParseException.class,
            () -> new MolangParser("a = 1").parse()
        );
        assertEquals(2, assign.position());

        var character = assertThrows(
            MolangParseException.class,
            () -> new MolangParser("1 # 2").parse()
        );
        assertEquals(2, character.position());

        var string = assertThrows(
            MolangParseException.class,
            () -> new MolangParser("1 + 'abc").parse()
        );
        assertEquals(4, string.position());
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
            "1; 2 3",
            "{1; 2",
            "'abc",
            "'a'b'",
            "'it''s'",
            "1 }",
            "1e",
            "1e+",
            "2ex",
            "1e2.5"
        );
    }
}
