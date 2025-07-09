package net.hollowcube.molang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MolangLexer {
    public enum Tok {
        PLUS, MINUS, STAR, SLASH,
        LPAREN, RPAREN, LBRACE, RBRACK,
        DOT, COMMA, COLON, QUESTION, QUESTIONQUESTION,
        GTE, GE, LTE, LE, EQ, NEQ, SEMICOLON,
        NUMBER, IDENT;
    }

    private final String source;

    private int start = 0;
    private int cursor = 0;

    public MolangLexer(@NotNull String source) {
        this.source = source;
    }

    /**
     * Returns the next token in the input, or null if the end of file was reached.
     *
     * @throws IllegalStateException if there is an unexpected token.
     */
    public @Nullable Tok next() {
        start = cursor;

        if (atEnd()) return null;

        consumeWhitespace();

        char c = advance();
        if (isAlpha(c))
            return ident();
        if (isDigit(c))
            return number();

        return symbol(c);
    }

    /**
     * Returns the next token <i>without</i> stepping to the next token in the input,
     * or null if the end of file was reached.
     *
     * @throws IllegalStateException if there is an unexpected token.
     */
    public @Nullable Tok peek() {
        var result = next();
        cursor = start; // Reset to where it was before the call to next.
        return result;
    }

    public void expect(@NotNull Tok type) {
        var next = next();
        if (next != type)
            throw new IllegalStateException("Expected " + type + " but got " + next);
    }

    public @NotNull String span() {
        return source.substring(start, cursor).strip();
    }

    private void consumeWhitespace() {
        while (true) {
            switch (peek0()) {
                case ' ', '\t', '\r', '\n' -> advance();
                default -> {
                    return;
                }
            }
        }
    }

    private Tok ident() {
        while (isAlpha(peek0()) || isDigit(peek0())) {
            advance();
        }

        return Tok.IDENT;
    }

    private Tok number() {
        // Pre decimal
        while (isDigit(peek0()))
            advance();

        // Decimal, if present
        if (match('.')) {
            while (isDigit(peek0()))
                advance();
        }

        return Tok.NUMBER;
    }

    private Tok symbol(char c) {
        return switch (c) {
            case '+' -> Tok.PLUS;
            case '-' -> Tok.MINUS;
            case '*' -> Tok.STAR;
            case '/' -> Tok.SLASH;
            case '.' -> Tok.DOT;
            case ',' -> Tok.COMMA;
            case '?' -> match('?') ? Tok.QUESTIONQUESTION : Tok.QUESTION;
            case ':' -> Tok.COLON;
            case '(' -> Tok.LPAREN;
            case ')' -> Tok.RPAREN;
            case '{' -> Tok.LBRACE;
            case '}' -> Tok.RBRACK;
            case ';' -> Tok.SEMICOLON;
            case '>' -> match('=') ? Tok.GTE : Tok.GE;
            case '<' -> match('=') ? Tok.LTE : Tok.LE;
            case '=' -> match('=') ? Tok.EQ : unexpected(c);
            case '!' -> match('=') ? Tok.NEQ : unexpected(c);
            default -> unexpected(c);
        };
    }

    private boolean atEnd() {
        return cursor >= source.length();
    }

    private char peek0() {
        if (atEnd())
            return '\u0000';
        return source.charAt(cursor);
    }

    private char advance() {
        if (atEnd()) throw new IllegalStateException("unexpected end of input");
        return source.charAt(cursor++);
    }

    private boolean match(char c) {
        if (atEnd()) return false;
        if (peek0() != c) return false;
        advance();
        return true;
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private Tok unexpected(char c) {
        throw new IllegalStateException(String.format("unexpected token '%s' at %d.", c, cursor));
    }
}

