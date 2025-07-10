# Molang

[![license](https://img.shields.io/github/license/hollow-cube/molang.svg)](LICENSE)

A parser and evaluator
for [Molang](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/molangreference/examples/molangconcepts/molangintroduction).

This library is a successor to our prior [MQL](https://github.com/hollow-cube/mql) library. Currently it does not
support runtime compilation to bytecode, but this is planned for the future as an optional addon module.

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

You can parse a molang expression using `MolangExpr.parseOrThrow(String)`:

```java
var expr = MolangExpr.parseOrThrow("1 + 2"); // Single expr

var expr = MolangExpr.parseOrThrow("""
            temp.x = 1 + 2 + 3;
            v.y = temp.x + 2;
        """, true); // Multi-line expr
```

You can then evaluate the expression using a `MolangEvaluator`:

```java
var evaluator = new MolangEvaluator(Map.of());
var result = evaluator.evaluate(expr); // Returns a double

// You can evaluate multiple times using the same evaluator (shared variable context)
var result2 = evaluator.evaluate(expr);

// You can read a variable from the evaluator
var y = evaluator.getVariable("y");
```

### Custom Queries

Custom query objects can be passed to the evaluator. By convention, you should alias query objects to their first letter
in the context. For example, if you add a (standard) `query` object, you should alias it to `q` also.

You can add globals by adding `Function` or `Num` values to the context map.

The names `math`, `m`, `variable`, `v`, `temp`, and `t` are reserved and will be overwritten by the evaluator.

### Optimizer

Molang ships with a basic static optimizer that can be used to do constant folding on an expression. This can be useful
for entirely removing molang evaluation for constant expressions.

```java
var expr = MolangExpr.parseOrThrow("1 + 2 + 3");
var optimized = MolangOptimizer.optimize(expr); // Returns a new expression
// optimized is now `new MolangExpr.Num(6)`
```

## Contributing

Contributions via PRs and issues are always welcome.

## License

This project is licensed under the [MIT License](LICENSE).
