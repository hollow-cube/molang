package net.hollowcube.molang;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/// Process-wide ids for variable names, so a [MolangProgram] compiled once can address
/// the variables of any [MolangState] by index instead of by name.
///
/// A [Symbol] lives only while something uses it: the programs that reference the name, and the
/// variable slots currently holding a value for it. Once none do, the name is forgotten and its id
/// reused, so loading expressions over the life of a server does not grow this registry. Every array
/// indexed by id also records which symbol owns each slot, so a reused id never reads a value written
/// under another name.
final class Symbols {

    /// A normalized name and its id. Compared by identity.
    static final class Symbol {
        final String name;
        final int id;

        private Symbol(String name, int id) {
            this.name = name;
            this.id = id;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class Entry extends WeakReference<Symbol> {
        final String name;
        final int id;

        Entry(Symbol symbol, ReferenceQueue<Symbol> queue) {
            super(symbol, queue);
            this.name = symbol.name;
            this.id = symbol.id;
        }
    }

    private static final ConcurrentHashMap<String, Entry> BY_NAME = new ConcurrentHashMap<>();
    private static final ReferenceQueue<Symbol> COLLECTED = new ReferenceQueue<>();
    private static final ReentrantLock LOCK = new ReentrantLock(); // Guards creating symbols and the free ids
    private static int[] freeIds = new int[16];
    private static int freeCount = 0;
    private static int nextId = 0;

    /// The symbol for a name (in any case), creating it if needed.
    static Symbol intern(String name) {
        String key = MolangExpr.normalizeName(name);
        Entry entry = BY_NAME.get(key);
        Symbol symbol = entry == null ? null : entry.get();
        return symbol != null ? symbol : internSlow(key);
    }

    /// The symbol for a name (in any case) if one is alive, without creating one.
    static @Nullable Symbol find(String name) {
        Entry entry = BY_NAME.get(MolangExpr.normalizeName(name));
        return entry == null ? null : entry.get();
    }

    /// The number of names currently alive, for tests.
    static int size() {
        LOCK.lock();
        try {
            expunge();
            return BY_NAME.size();
        } finally {
            LOCK.unlock();
        }
    }

    private static Symbol internSlow(String key) {
        LOCK.lock();
        try {
            expunge();
            Entry entry = BY_NAME.get(key);
            Symbol symbol = entry == null ? null : entry.get();
            if (symbol != null) return symbol;
            int id = freeCount > 0 ? freeIds[--freeCount] : nextId++;
            symbol = new Symbol(key, id);
            BY_NAME.put(key, new Entry(symbol, COLLECTED));
            return symbol;
        } finally {
            LOCK.unlock();
        }
    }

    private static void expunge() {
        for (Entry entry; (entry = (Entry) COLLECTED.poll()) != null;) {
            BY_NAME.remove(entry.name, entry);
            if (freeCount == freeIds.length) freeIds = Arrays.copyOf(freeIds, freeCount * 2);
            freeIds[freeCount++] = entry.id;
        }
    }

    private Symbols() {}
}
