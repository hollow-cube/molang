package net.hollowcube.molang.runtime;

import org.jetbrains.annotations.Nullable;

/// A problem found while evaluating an expression, eg a division by zero, which evaluated to 0 rather
/// than failing.
///
/// @param message  what went wrong
/// @param source   the source of the expression, if it was compiled from source
/// @param position where in the source the problem is, or -1 if it is not known
public record ContentError(String message, @Nullable String source, int position) {

    public ContentError(String message) {
        this(message, null, -1);
    }

    /// The 1-based line of the problem, or -1 if its position or source is not known.
    public int line() {
        if (source == null || position < 0) return -1;
        int line = 1;
        for (int i = 0; i < Math.min(position, source.length()); i++)
            if (source.charAt(i) == '\n') line++;
        return line;
    }

    /// The 1-based column of the problem within its line, or -1 if its position or source is not known.
    public int column() {
        if (source == null || position < 0) return -1;
        int at = Math.min(position, source.length());
        return at - (source.lastIndexOf('\n', at - 1) + 1) + 1;
    }

    @Override
    public String toString() {
        if (line() > 0) return message + " at line " + line() + ", column " + column();
        if (position >= 0) return message + " at " + position;
        return message;
    }
}
