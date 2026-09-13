package net.hollowcube.molang;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/// The names an expression can read, and what they are bound to: typically `query`, as functions of
/// a host context `T` (eg the entity being animated) that is passed to each evaluation.
///
/// Programs are compiled against an environment, which resolves static names while compiling.
/// Build one environment per kind of context and share it.
///
/// ```java
/// record Ctx(Entity entity, double animTime) {}
///
/// static final MolangEnvironment<Ctx> ENV = MolangEnvironment.<Ctx>builder()
///     .query(q -> q
///         .number("anim_time", Ctx::animTime)
///         .bool("is_on_ground", ctx -> ctx.entity().isOnGround())
///         .stringFunction("is_item_equipped", (ctx, slot) -> ...))
///     .build();
///
/// MolangProgram<Ctx> program = ENV.compile("math.sin(q.anim_time * 90)");
/// double value = program.eval(state, new Ctx(entity, time));
/// ```
///
/// Names are case-insensitive. `math`, `variable` and `temp` (and their aliases `m`, `v` and `t`)
/// are provided by every environment, and `return`, `loop`, `for_each`, `break`, `continue` and
/// `this` are keywords, so none of them can be registered at the top level.
public final class MolangEnvironment<T> {
    public static final Set<String> RESERVED_NAMES = Set.of(
        "math",
        "m",
        "variable",
        "v",
        "temp",
        "t",
        "return",
        "loop",
        "for_each",
        "break",
        "continue",
        "this"
    );

    private static final MolangEnvironment<Object> EMPTY = new MolangEnvironment<>(
        new Entry.Names("", Map.of(), null),
        null
    );

    /// The top level names, and the resolver registered with `global`, if any.
    final Entry.Names root;
    /// Names the host binds under `variable`, or null if every variable is the state's own.
    final Entry.@Nullable Names variables;

    private MolangEnvironment(Entry.Names root, Entry.@Nullable Names variables) {
        this.root = root;
        this.variables = variables;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /// An environment with only the builtin names, for expressions that read no host values.
    public static MolangEnvironment<Object> empty() {
        return EMPTY;
    }

    /// Parses and compiles source, whose content errors then include their position in it.
    ///
    /// @throws MolangParseException if the source is not a valid expression
    public MolangProgram<T> compile(String source) {
        return MolangProgram.compile(this, new MolangParser(source).parse(), source, true);
    }

    public static final class Builder<T> {
        private final Namespace<T> root = new Namespace<>("");
        private @Nullable Namespace<T> variables;

        private Builder() {}

        /// Registers names under `query`, also reachable as `q`.
        public Builder<T> query(Consumer<Namespace<T>> names) {
            return namespace("query", names).alias("q", "query");
        }

        /// Registers names under `context`, also reachable as `c`.
        public Builder<T> context(Consumer<Namespace<T>> names) {
            return namespace("context", names).alias("c", "context");
        }

        /// Binds names under `variable` (and `v`) to host values, eg to expose an entity's position as
        /// `v.x`. Bound names are read-only: assigning to one is a content error. Every other variable
        /// is still the state's own, unless the namespace has a [Namespace#dynamic(DynamicResolver)]
        /// fallback, which then answers (and makes read-only) every other variable too.
        public Builder<T> variables(Consumer<Namespace<T>> names) {
            if (variables == null) variables = new Namespace<>("variable");
            names.accept(variables);
            return this;
        }

        /// Registers names under a top level namespace. Registering the same namespace again adds to it.
        public Builder<T> namespace(String name, Consumer<Namespace<T>> names) {
            root.namespace(name, names);
            return this;
        }

        /// Registers names at the top level, eg a function called as `name()`. A
        /// [Namespace#dynamic(DynamicResolver)] fallback here answers every top level name that is not
        /// registered or built in.
        public Builder<T> global(Consumer<Namespace<T>> names) {
            names.accept(root);
            return this;
        }

        /// Makes `alias` another name for the top level name `target`, as `q` is for `query`.
        public Builder<T> alias(String alias, String target) {
            root.alias(alias, target);
            return this;
        }

        public MolangEnvironment<T> build() {
            return new MolangEnvironment<>(
                new Entry.Names(root.path, root.freeze(), root.fallback),
                variables == null
                    ? null
                    : new Entry.Names(variables.path, variables.freeze(), variables.fallback)
            );
        }
    }

    /// A set of names, eg everything under `query`.
    public static final class Namespace<T> {
        private final String path;
        private final Map<String, Object> entries = new HashMap<>(); // Entry, or Namespace while building
        private @Nullable DynamicResolver<T> fallback;

        private Namespace(String path) {
            this.path = path;
        }

        /// A number that may be missing: null when `value` gives null, so `??` can replace it.
        public Namespace<T> optionalNumber(String name, Function<T, @Nullable Double> value) {
            return put(name, new Entry.OptionalNumber<>(value));
        }

        /// A number, eg `q.anim_time`.
        public Namespace<T> number(String name, ToDoubleFunction<T> value) {
            return put(name, new Entry.Number<>(value));
        }

        /// A boolean, which Molang sees as 1 or 0.
        public Namespace<T> bool(String name, Predicate<T> value) {
            return put(name, new Entry.Bool<>(value));
        }

        /// A string, eg an entity's identifier.
        public Namespace<T> string(String name, Function<T, String> value) {
            return put(name, new Entry.Text<>(value));
        }

        /// A number that never changes, which compiles into the expression. The same as a `pure`
        /// function of no arguments.
        public Namespace<T> constant(String name, double value) {
            return pure(name, () -> value);
        }

        public Namespace<T> function(String name, Function1<T> function) {
            return put(name, new Entry.Fn1<>(function));
        }

        public Namespace<T> function(String name, Function2<T> function) {
            return put(name, new Entry.Fn2<>(function));
        }

        public Namespace<T> function(String name, Function3<T> function) {
            return put(name, new Entry.Fn3<>(function));
        }

        /// A function of one string, eg `q.is_item_equipped('main_hand')`.
        public Namespace<T> stringFunction(String name, StringFunction<T> function) {
            return put(name, new Entry.FnString<>(function));
        }

        /// A function of any number of numbers.
        public Namespace<T> variadic(String name, VariadicFunction<T> function) {
            return put(name, new Entry.FnVariadic<>(function));
        }

        /// A function of any arguments, returning any value, for what the typed forms cannot express.
        public Namespace<T> value(String name, ValueFunction<T> function) {
            return put(name, new Entry.FnValue<>(function));
        }

        /// A function that depends only on its arguments, so calls with constant arguments are
        /// evaluated while compiling. A pure function of no arguments is a constant.
        public Namespace<T> pure(String name, DoubleSupplier function) {
            return put(name, new Entry.Pure0(function));
        }

        public Namespace<T> pure(String name, DoubleUnaryOperator function) {
            return put(name, new Entry.Pure1(function));
        }

        public Namespace<T> pure(String name, DoubleBinaryOperator function) {
            return put(name, new Entry.Pure2(function));
        }

        public Namespace<T> pure(String name, PureFunction3 function) {
            return put(name, new Entry.Pure3(function));
        }

        /// A nested namespace, eg `q.rider.yaw`. Registering the same namespace again adds to it.
        public Namespace<T> namespace(String name, Consumer<Namespace<T>> names) {
            String key = key(name);
            Object existing = entries.get(key);
            Namespace<T> namespace;
            if (existing instanceof Namespace<?> ns) {
                @SuppressWarnings("unchecked") Namespace<T> typed = (Namespace<T>) ns;
                namespace = typed;
            } else {
                namespace = new Namespace<>(path.isEmpty() ? key : path + "." + key);
                put(name, namespace);
            }
            names.accept(namespace);
            return this;
        }

        /// A namespace whose names are only known at runtime. Everything under it, however deeply
        /// nested, is read by calling `resolver` with the rest of the name (eg `a.b` for `ns.a.b`)
        /// and the arguments, if it was called. A resolver returns [MolangValue#NULL] for a name it does
        /// not have.
        public Namespace<T> dynamic(String name, DynamicResolver<T> resolver) {
            return put(name, new Entry.Dynamic<>(resolver));
        }

        /// Answers every name in this namespace that is not registered, with the rest of the name
        /// (eg `a.b` for `ns.a.b`) and the arguments, if it was called. The resolver returns
        /// [MolangValue#NULL] for a name it does not have. Names it answers are never reported as
        /// unresolved.
        public Namespace<T> dynamic(DynamicResolver<T> resolver) {
            if (fallback != null)
                throw new IllegalArgumentException(
                    (path.isEmpty() ? "The top level" : "'" + path + "'")
                        + " already has a dynamic resolver"
                );
            fallback = resolver;
            return this;
        }

        private Namespace<T> put(String name, Object entry) {
            String key = key(name);
            if (entries.containsKey(key))
                throw new IllegalArgumentException(
                    "'" + qualified(key) + "' is already registered"
                );
            entries.put(key, entry);
            return this;
        }

        private void alias(String alias, String target) {
            Object entry = entries.get(key(target));
            if (entry == null)
                throw new IllegalArgumentException("'" + target + "' is not registered");
            Object existing = entries.get(key(alias));
            if (existing != null && existing != entry)
                throw new IllegalArgumentException(
                    "'" + qualified(key(alias)) + "' is already registered"
                );
            entries.put(key(alias), entry);
        }

        private String key(String name) {
            String key = MolangExpr.normalizeName(name);
            if (!key.matches("[a-z_][a-z0-9_]*"))
                throw new IllegalArgumentException("'" + name + "' is not a valid Molang name");
            if (path.isEmpty() && RESERVED_NAMES.contains(key))
                throw new IllegalArgumentException("'" + key + "' is reserved");
            return key;
        }

        private String qualified(String key) {
            return path.isEmpty() ? key : path + "." + key;
        }

        private Map<String, Entry> freeze() {
            var frozen = new IdentityHashMap<Object, Entry>();
            var result = new HashMap<String, Entry>();
            for (var e : entries.entrySet()) {
                // Aliases share one frozen entry
                Entry entry = frozen.computeIfAbsent(
                    e.getValue(),
                    value -> value instanceof Namespace<?> ns
                        ? new Entry.Names(ns.path, ns.freeze(), ns.fallback)
                        : (Entry) value
                );
                result.put(e.getKey(), entry);
            }
            return Map.copyOf(result);
        }
    }

    @FunctionalInterface
    public interface Function1<T> {
        double apply(T context, double a);
    }

    @FunctionalInterface
    public interface Function2<T> {
        double apply(T context, double a, double b);
    }

    @FunctionalInterface
    public interface Function3<T> {
        double apply(T context, double a, double b, double c);
    }

    @FunctionalInterface
    public interface StringFunction<T> {
        double apply(T context, String a);
    }

    @FunctionalInterface
    public interface VariadicFunction<T> {
        double apply(T context, double[] args);
    }

    @FunctionalInterface
    public interface ValueFunction<T> {
        MolangValue apply(T context, List<MolangValue> args);
    }

    @FunctionalInterface
    public interface PureFunction3 {
        double apply(double a, double b, double c);
    }

    @FunctionalInterface
    public interface DynamicResolver<T> {
        MolangValue resolve(T context, String name, List<MolangValue> args);
    }

    /// What a name is bound to, as the compiler sees it.
    sealed interface Entry {
        record Names(
            String path,
            Map<String, Entry> entries,
            @Nullable DynamicResolver<?> fallback
        ) implements Entry {}

        record OptionalNumber<T>(Function<T, @Nullable Double> value) implements Entry {}

        record Number<T>(ToDoubleFunction<T> value) implements Entry {}

        record Bool<T>(Predicate<T> value) implements Entry {}

        record Text<T>(Function<T, String> value) implements Entry {}

        record Fn1<T>(Function1<T> function) implements Entry {}

        record Fn2<T>(Function2<T> function) implements Entry {}

        record Fn3<T>(Function3<T> function) implements Entry {}

        record FnString<T>(StringFunction<T> function) implements Entry {}

        record FnVariadic<T>(VariadicFunction<T> function) implements Entry {}

        record FnValue<T>(ValueFunction<T> function) implements Entry {}

        record Pure0(DoubleSupplier function) implements Entry {}

        record Pure1(DoubleUnaryOperator function) implements Entry {}

        record Pure2(DoubleBinaryOperator function) implements Entry {}

        record Pure3(PureFunction3 function) implements Entry {}

        record Dynamic<T>(DynamicResolver<T> resolver) implements Entry {}
    }
}
