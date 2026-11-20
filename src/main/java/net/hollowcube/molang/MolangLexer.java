package net.hollowcube.molang;

import org.jetbrains.annotations.Nullable;

final class MolangLexer {
    public enum Tok {
        PLUS,
        MINUS,
        STAR,
        SLASH,
        SLASLA,
        LPAREN,
        RPAREN,
        LBRACE,
        RBRACK,
        DOT,
        COMMA,
        COLON,
        QUESTION,
        QUESTIONQUESTION,
        GTEQ,
        GT,
        LTEQ,
        LT,
        EQ,
        EQEQ,
        BANGEQ,
        SEMICOLON,
        AMPAMP,
        BARBAR,
        BANG,
        NUMBER,
        STRING,
        IDENT,
    }

    private final String source;

    private int start = 0;
    private int tokenStart = 0; // start, after leading whitespace
    private int cursor = 0;

    public MolangLexer(String source) {
        this.source = source;
    }

    /**
     * Returns the next token in the input, or null if the end of file was reached.
     *
     * @throws MolangParseException if there is an unexpected token.
     */
    public @Nullable Tok next() {
        start = cursor;

        consumeWhitespace();
        tokenStart = cursor;
        if (atEnd()) return null;

        char c = advance();
        if (isAlpha(c)) return ident();
        if (isDigit(c)) return number();
        if (c == '\'') return string();

        return symbol(c);
    }

    /**
     * Returns the next token <i>without</i> stepping to the next token in the input,
     * or null if the end of file was reached.
     *
     * @throws MolangParseException if there is an unexpected token.
     */
    public @Nullable Tok peek() {
        var result = next();
        cursor = start; // Reset to where it was before the call to next.
        return result;
    }

    public void expect(Tok type) {
        var next = next();
        if (next != type)
            throw error(
                next == null
                    ? "expected " + type + " but reached the end"
                    : "expected " + type + " but got " + next
            );
    }

    /// Where the last token returned by [#next()] starts, or the end of input if there was none.
    public int tokenStart() {
        return tokenStart;
    }

    /// A parse error at the last token returned by [#next()].
    public MolangParseException error(String reason) {
        return error(reason, tokenStart);
    }

    public MolangParseException error(String reason, int position) {
        return new MolangParseException(reason, source, position);
    }

    public String span() {
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

    /// A single quoted string. Molang has no escapes, so a string cannot contain `'`.
    private Tok string() {
        while (!atEnd() && peek0() != '\'') advance();
        if (atEnd()) throw new MolangParseException("unterminated string", source, tokenStart);
        advance(); // Closing quote
        return Tok.STRING;
    }

    private Tok number() {
        // Pre decimal
        while (isDigit(peek0())) advance();

        // Decimal, if present
        if (match('.')) {
            while (isDigit(peek0())) advance();
        }

        // Exponent, if present (eg 3.27e-7). Molang has none, but numbers written by other tools
        // often do, and `e` directly after a number is otherwise a syntax error.
        char e = peek0();
        if (e == 'e' || e == 'E') {
            int digits = peekAt(1) == '+' || peekAt(1) == '-' ? 2 : 1;
            if (isDigit(peekAt(digits))) {
                cursor += digits;
                while (isDigit(peek0())) advance();
            }
        }

        return Tok.NUMBER;
    }

    private Tok symbol(char c) {
        return switch (c) {
            case '+' -> Tok.PLUS;
            case '-' -> Tok.MINUS;
            case '*' -> Tok.STAR;
            case '/' -> match('/') ? Tok.SLASLA : Tok.SLASH;
            case '.' -> Tok.DOT;
            case ',' -> Tok.COMMA;
            case '?' -> match('?') ? Tok.QUESTIONQUESTION : Tok.QUESTION;
            case ':' -> Tok.COLON;
            case '(' -> Tok.LPAREN;
            case ')' -> Tok.RPAREN;
            case '{' -> Tok.LBRACE;
            case '}' -> Tok.RBRACK;
            case ';' -> Tok.SEMICOLON;
            case '>' -> match('=') ? Tok.GTEQ : Tok.GT;
            case '<' -> match('=') ? Tok.LTEQ : Tok.LT;
            case '=' -> match('=') ? Tok.EQEQ : Tok.EQ;
            case '!' -> match('=') ? Tok.BANGEQ : Tok.BANG;
            case '&' -> match('&') ? Tok.AMPAMP : unexpected(c);
            case '|' -> match('|') ? Tok.BARBAR : unexpected(c);
            default -> unexpected(c);
        };
    }

    private boolean atEnd() {
        return cursor >= source.length();
    }

    private char peek0() {
        if (atEnd()) return '\u0000';
        return source.charAt(cursor);
    }

    private char peekAt(int offset) {
        int index = cursor + offset;
        return index < source.length() ? source.charAt(index) : '\u0000';
    }

    private char advance() {
        if (atEnd()) throw new MolangParseException("unexpected end of input", source, cursor);
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
        throw new MolangParseException("unexpected character '" + c + "'", source, cursor - 1);
    }
}
