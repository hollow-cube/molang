package net.hollowcube.molang;

import net.hollowcube.molang.Symbols.Symbol;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/// Read/write storage (canonically for `variable` and `temp`), indexed by [Symbol] id so compiled
/// programs can read and write numbers without looking up names or boxing.
///
/// A slot belongs to the symbol in `owners` (which keeps that symbol, and so its id, alive while the
/// slot holds a value). It holds a number unless its ref holds another value, and is unset when its
/// generation is stale, so clearing (once per evaluation for temps) is a counter increment. `refs`
/// is only allocated once a non-number (eg a string) is stored, since most scripts store only numbers.
final class VariableStore {
    private Symbol[] owners = new Symbol[0];
    private double[] numbers = new double[0];
    private MolangValue @Nullable [] refs = null; // A slot's non-number value, or null for a number
    private int[] generations = new int[0];
    private int generation = 1;

    double getNumber(Symbol symbol) {
        int id = symbol.id;
        return id < owners.length
            && owners[id] == symbol
            && generations[id] == generation
            && (refs == null || refs[id] == null)
            ? numbers[id]
            : Double.NaN;
    }

    MolangValue get(@Nullable Symbol symbol) {
        if (symbol == null) return MolangValue.NULL;
        int id = symbol.id;
        if (id >= owners.length || owners[id] != symbol || generations[id] != generation)
            return MolangValue.NULL;
        MolangValue ref = refs == null ? null : refs[id];
        return ref == null ? new MolangValue.Num(numbers[id]) : ref;
    }

    MolangValue get(String name) {
        return get(Symbols.find(name));
    }

    void setNumber(Symbol symbol, double value) {
        int id = claim(symbol);
        numbers[id] = value;
        if (refs != null) refs[id] = null;
    }

    void set(Symbol symbol, MolangValue value) {
        if (value instanceof MolangValue.Num(double number)) {
            setNumber(symbol, number);
            return;
        }
        int id = claim(symbol);
        if (refs == null) refs = new MolangValue[owners.length];
        refs[id] = value;
    }

    void clear() {
        if (++generation == 0) {
            Arrays.fill(generations, 0);
            generation = 1;
        }
    }

    private int claim(Symbol symbol) {
        int id = symbol.id;
        if (id >= owners.length) {
            int size = Math.max(id + 1, owners.length * 2);
            owners = Arrays.copyOf(owners, size);
            numbers = Arrays.copyOf(numbers, size);
            if (refs != null) refs = Arrays.copyOf(refs, size);
            generations = Arrays.copyOf(generations, size);
        }
        owners[id] = symbol;
        generations[id] = generation;
        return id;
    }
}
