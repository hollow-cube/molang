package net.hollowcube.molang.runtime;

/// Thrown by a host function to report a content error (eg an argument out of range), which the
/// evaluation records at the call and evaluates to 0.
public class MolangContentException extends RuntimeException {
    public MolangContentException(String message) {
        super(message);
    }
}
