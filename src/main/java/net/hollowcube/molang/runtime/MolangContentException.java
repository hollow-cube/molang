package net.hollowcube.molang.runtime;

public class MolangContentException extends RuntimeException {
    public MolangContentException(String message) {
        super(message);
    }

    public ContentError toContentError() {
        return new ContentError(getMessage());
    }
}
