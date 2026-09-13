# Molang

[![license](https://img.shields.io/github/license/hollow-cube/molang.svg)](LICENSE)

A parser and evaluator
for [Molang](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/molangreference/examples/molangconcepts/molangintroduction).

This library is a successor to our prior [MQL](https://github.com/hollow-cube/mql) library. Expressions are compiled
into an efficient tree of specialized nodes, there is no runtime class loading so it is safe for native image.

## Feature Support

- [x] Basic operators (supported unless mentioned otherwise)
- [x] Variables (persistent and temporary)
- [x] Builtin math libraries
- [x] Custom query objects
- [ ] Structs
- [ ] Arrays
- [ ] Cross-object accessors (arrow operator)

## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.hollowcube:molang:<latest release>")
}
```

## Usage

```java
double result = MolangProgram.compile("math.sqrt(16) + 1")
        .eval(new MolangState()); // 5.0
```

Programs are compiled once and evaluated many times, and variables persist in the state between evaluations:

```java
var counter = MolangProgram.compile("v.x = (v.x ?? 0) + 1; v.x");
var state = new MolangState();
counter.eval(state); // 1.0
counter.eval(state); // 2.0
```

Compile source into a `MolangProgram` against a `MolangEnvironment`, which describes the names expressions can read,
then evaluate it with a `MolangState`, which holds one entity's variables.

```java
record Ctx(Entity entity, double animTime) {}

static final MolangEnvironment<Ctx> ENV = MolangEnvironment.<Ctx>builder()
        .query(q -> q  // query, also reachable as q
                .number("anim_time", Ctx::animTime)
                .bool("is_on_ground", ctx -> ctx.entity().isOnGround())
                .stringFunction("is_item_equipped", (ctx, slot) -> ctx.entity().hasItem(slot) ? 1 : 0))
        .build();

var program = ENV.compile("math.sin(q.anim_time * 90)");

// Scripts of several statements work the same way
var script = ENV.compile("""
     temp.x = 1 + 2 + 3;
     v.y = temp.x + 2;
     """);

var state = new MolangState();
var result = program.eval(state, new Ctx(entity, time)); // Returns a double

var y = state.getVariable("y"); // 8.0

// Content errors from execution, eg "Division by zero at line 2, column 5"
var errors = state.getErrors();
```

Environments and programs are immutable and can be shared between threads, so build the environment once, compile each
expression once (eg when loading a model) and keep a state per context (ie entity). Expressions that read no host values
can use `MolangProgram.compile(source)` and `program.eval(state)`.

### Analysis

`program.analysis()` describes what a program depends on, worked out while compiling:

- `constant()`: its value if it was folded entirely, eg to skip evaluating it. 
  - Eg the first example would hold a constant of 5.0
- `hostNames()`, `dynamicNames()` and `unresolvedNames()`: the names it reads, as canonical names (`q.x` is reported
  as `query.x`). Unresolved names evaluate to null, so a loader may want to warn about them.
- `variablesRead()`, `variablesWritten()`, `usesTemps()` and `random()`.
- `dependsOnlyOn(names)`: whether its result depends only on the given host names, eg to precompute the frames of an
  animation that reads nothing but `query.anim_time`.

The analysis is conservative: a name in a branch that folding removed is still reported.

### Environments

A namespace can hold:

- `number`, `bool` and `string` values of the context.
- `function`s of one to three numbers, a `stringFunction` of one string, and `variadic` functions of any number of
  numbers. Arguments of the wrong type are content errors.
- `pure` functions of zero to three numbers, which do not read the context, so calls with constant arguments fold, and
  `constant` numbers (a pure function of no arguments).
- `value` functions taking and returning any `MolangValue`, for what the typed forms cannot express.
- `optionalNumber` values, which are null when missing (so `??` replaces them).
- nested `namespace`s (eg `q.rider.yaw`), and `dynamic` namespaces whose names are only known at runtime. A namespace
  can also have a `dynamic` fallback that answers every name it does not register.

`variables` binds read-only host values under `variable` (`v`), eg an entity's position as `v.x`; other variables stay
the state's own unless that namespace has a `dynamic` fallback. `query` (`q`) and `context` (`c`) have builder methods,
`namespace` registers any other top level name, and `global` registers names at the top level, eg a function called as
`name()`. Names are case-insensitive. `math`, `variable` and `temp` (and `m`, `v` and `t`) are built in, and `return`,
`loop`, `for_each`, `break`, `continue` and `this` are keywords, so none of them can be registered at the top level.

Host functions report problems by throwing `MolangContentException`, which becomes a content error that evaluates to 0; any
other exception is also reported as a content error.

## Language Extensions

Beyond [Bedrock's Molang](https://learn.microsoft.com/en-us/minecraft/creator/documents/molang/syntax-guide), this
library accepts:

- **Exponent literals**, eg `3.27e-7` or `1E3`. Bedrock has no exponent syntax, but numbers written by other tools (eg
  JavaScript's `String(1e-7)` or JSON serializers) often use it, and a number directly followed by `e` is otherwise a
  syntax error, so no valid expression changes meaning.
- **Unary `+`**, eg `2 * +3`, which gives its operand as a number.
- **`m` as an alias of `math`**, alongside Bedrock's `q`, `v`, `t` and `c`.
- **Top level functions**, eg `name()`, when the host registers them with `global`.

### Differences from Bedrock

- A complex expression without `return` evaluates to its last statement, rather than 0.
- A trailing `;` is allowed in a simple expression.
- `math.sign(0)` is 0, rather than -1.

## Contributing

Contributions via PRs and issues are always welcome.

## License

This project is licensed under the [MIT License](LICENSE).
