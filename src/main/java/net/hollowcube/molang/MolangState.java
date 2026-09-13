package net.hollowcube.molang;

import net.hollowcube.molang.runtime.ContentError;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Execution state for one context (eg an entity): its variables, shared by every program evaluated
/// with it, and the content errors of the last evaluation. Not thread-safe.
public final class MolangState {
    public static final int MAX_LOOP_COUNTER = 1024; // Arbitrary limit to prevent massive loops.

    final List<ContentError> errors = new ArrayList<>();
    boolean loopScope = false; // Whether we are currently in a looping scope (to catch break/continues)
    int loopCounter = MAX_LOOP_COUNTER;

    final VariableStore variable;
    final VariableStore temp = new VariableStore();
    /// The source of the program being evaluated, for content errors, or null if it is not known.
    @Nullable
    String source;

    public MolangState() {
        this.variable = new VariableStore();
    }

    private MolangState(MolangState other) {
        this.variable = other.variable.copy();
        this.errors.addAll(other.errors);
        this.source = other.source;
    }

    /// An independent state starting with this one's variables and errors, eg to evaluate ahead
    /// without disturbing this state. Changes to either do not affect the other.
    public MolangState copy() {
        return new MolangState(this);
    }

    /// The value of `variable.name`, or [MolangValue#NULL] if it is unset.
    public MolangValue getVariable(String name) {
        return variable.get(name);
    }

    /// Sets `variable.name`, eg to initialize it before the first evaluation.
    public void setVariable(String name, MolangValue value) {
        variable.set(Symbols.intern(name), value);
    }

    public void setVariable(String name, double value) {
        variable.setNumber(Symbols.intern(name), value);
    }

    /// Unsets every `variable`.
    public void clearVariables() {
        variable.clear();
    }

    /// The content errors reported by the last evaluation, eg a division by zero (which evaluates
    /// to 0). They are cleared when the next evaluation starts.
    public List<ContentError> getErrors() {
        return errors;
    }

    void begin(@Nullable String source) {
        this.source = source;
        loopCounter = MAX_LOOP_COUNTER;
        if (!errors.isEmpty()) errors.clear();
        temp.clear();
    }

    void error(String message, int position) {
        errors.add(new ContentError(message, source, position));
    }

    // Control flow exceptions below. They carry no stack trace, since they are not errors.

    static final class Break extends RuntimeException {
        static final Break INSTANCE = new Break();

        private Break() {
            super(null, null, false, false);
        }
    }

    static final class Continue extends RuntimeException {
        static final Continue INSTANCE = new Continue();

        private Continue() {
            super(null, null, false, false);
        }
    }

    static final class Return extends RuntimeException {
        /// `return;`, and any return of 0, which need no new instance.
        static final Return EMPTY = new Return(new MolangValue.Num(0.0), -1);

        final MolangValue value;
        final int position;

        Return(MolangValue value, int position) {
            super(null, null, false, false);
            this.value = value;
            this.position = position;
        }
    }
}
