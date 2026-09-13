package net.hollowcube.molang;

/// A Molang expression that could not be parsed, with where in its source the problem is.
public final class MolangParseException extends RuntimeException {
    private final String reason;
    private final String source;
    private final int position;

    MolangParseException(String reason, String source, int position) {
        super(reason + " at " + location(source, position));
        this.reason = reason;
        this.source = source;
        this.position = Math.clamp(position, 0, source.length());
    }

    /// What is wrong, without the location.
    public String reason() {
        return reason;
    }

    /// The whole source that failed to parse.
    public String source() {
        return source;
    }

    /// The index in [#source()] of the problem, which is the source's length for an unexpected end.
    public int position() {
        return position;
    }

    /// The 1-based line of the problem.
    public int line() {
        int line = 1;
        for (int i = 0; i < position; i++) if (source.charAt(i) == '\n') line++;
        return line;
    }

    /// The 1-based column of the problem within its line.
    public int column() {
        return position - (source.lastIndexOf('\n', position - 1) + 1) + 1;
    }

    private static String location(String source, int position) {
        position = Math.clamp(position, 0, source.length());
        int line = 1;
        for (int i = 0; i < position; i++) if (source.charAt(i) == '\n') line++;
        int column = position - (source.lastIndexOf('\n', position - 1) + 1) + 1;
        return "line " + line + ", column " + column;
    }
}
