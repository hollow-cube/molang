package net.hollowcube.molang;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestMolangLexer {

    @ParameterizedTest
    @MethodSource("individualSymbols")
    public void testIndividualSymbols(String input, MolangLexer.Tok expected) {
        var lexer = new MolangLexer(input);

        var token = lexer.next();
        assertNotNull(token);
        assertEquals(expected, token);

        var eof = lexer.next();
        assertNull(eof);
    }

    private static Stream<Arguments> individualSymbols() {
        return Stream.of(
                Arguments.of("+", MolangLexer.Tok.PLUS),
                Arguments.of("-", MolangLexer.Tok.MINUS),
                Arguments.of("*", MolangLexer.Tok.STAR),
                Arguments.of("/", MolangLexer.Tok.SLASH),
                Arguments.of(".", MolangLexer.Tok.DOT),
                Arguments.of(",", MolangLexer.Tok.COMMA),
                Arguments.of("?", MolangLexer.Tok.QUESTION),
                Arguments.of("??", MolangLexer.Tok.QUESTIONQUESTION),
                Arguments.of("(", MolangLexer.Tok.LPAREN),
                Arguments.of(")", MolangLexer.Tok.RPAREN),

                Arguments.of("123", MolangLexer.Tok.NUMBER),
                Arguments.of("123.", MolangLexer.Tok.NUMBER),
                Arguments.of("123.456", MolangLexer.Tok.NUMBER),

                Arguments.of("abc", MolangLexer.Tok.IDENT),
                Arguments.of("aBc", MolangLexer.Tok.IDENT),
                Arguments.of("aBc1", MolangLexer.Tok.IDENT)
        );
    }
}
