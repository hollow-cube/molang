package net.hollowcube.molang;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Variable names are only remembered while something uses them, and a reused id never exposes the
/// value of the name that had it before.
public class TestSymbols {

    @Test
    public void testUnusedNamesAreForgotten() throws InterruptedException {
        var name = "unused_" + System.nanoTime();
        var state = new MolangState();
        state.setVariable(name + "_a", 1);
        state.temp.setNumber(Symbols.intern(name + "_b"), 2);
        assertEquals(new MolangValue.Num(1), state.getVariable(name + "_a"));
        assertNotNull(Symbols.find(name + "_a"));

        state = null;
        awaitCollected(name + "_a", name + "_b");
        assertNull(Symbols.find(name + "_a"));
        assertNull(Symbols.find(name + "_b"));
    }

    @Test
    public void testReusedIdsDoNotSeeOtherNames() throws InterruptedException {
        var state = new MolangState();
        String[] names = new String[200];
        var oldIds = new HashSet<Integer>();
        for (int i = 0; i < names.length; i++) {
            names[i] = "old_" + i + "_" + System.nanoTime();
            state.setVariable(names[i], i);
            oldIds.add(Symbols.find(names[i]).id);
        }
        // A new state drops those variables, which frees their names and ids for reuse
        state = new MolangState();
        awaitCollected(names[0]);
        assertNull(Symbols.find(names[0]));

        var writer = new MolangState();
        String prefix = "new_" + System.nanoTime() + "_";
        var reused = new HashSet<Integer>();
        for (int i = 0; i < names.length; i++) {
            writer.setVariable(prefix + i, -1);
            if (oldIds.contains(Symbols.find(prefix + i).id)) reused.add(i);
        }
        assertFalse(reused.isEmpty(), "no ids were reused");
        for (int i = 0; i < names.length; i++) {
            assertEquals(MolangValue.NULL, state.getVariable(prefix + i));
            assertEquals(new MolangValue.Num(-1), writer.getVariable(prefix + i));
        }
    }

    @Test
    public void testTempsClearWithoutForgettingVariables() {
        var state = new MolangState();
        var name = Symbols.intern("temp_" + System.nanoTime());
        state.temp.setNumber(name, 3);
        state.setVariable("kept", 4);
        state.begin(null);
        assertEquals(MolangValue.NULL, state.temp.get(name));
        assertEquals(Double.NaN, state.temp.getNumber(name));
        assertEquals(new MolangValue.Num(4), state.getVariable("kept"));
    }

    @Test
    public void testNamesAreCaseInsensitive() {
        assertSame(Symbols.intern("Case_" + 1), Symbols.intern("CASE_1"));
        assertSame(Symbols.intern("case_1"), Symbols.find("cAsE_1"));
    }

    @Test
    public void testSlotsHoldNumbersOrValues() {
        var store = new VariableStore();
        var a = Symbols.intern("slot_a");
        var b = Symbols.intern("slot_b");
        store.setNumber(a, 1);
        assertEquals(1, store.getNumber(a));
        store.set(b, new MolangValue.Str("text")); // allocates refs
        assertEquals(new MolangValue.Str("text"), store.get(b));
        assertEquals(Double.NaN, store.getNumber(b));
        assertEquals(1, store.getNumber(a));
        store.setNumber(b, 2); // a number replaces the value
        assertEquals(new MolangValue.Num(2), store.get(b));
        store.set(a, MolangValue.NULL);
        assertEquals(MolangValue.NULL, store.get(a));
        assertEquals(Double.NaN, store.getNumber(a));
    }

    private static void awaitCollected(String... names) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            boolean alive = false;
            for (String name : names) alive |= Symbols.find(name) != null;
            if (!alive) return;
            System.gc();
            Thread.sleep(10);
        }
    }
}
