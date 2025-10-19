# Compiler Project

## Compiler Optimizations

This document describes the optimizations implemented in our JMM compiler. The compiler supports several optimization techniques that improve the efficiency and performance of the generated code.

### 1. Register Allocation

The compiler implements register allocation to efficiently use JVM local variables. This optimization is controlled via the `--r=<n>` command-line option, which determines how registers are allocated:

- **n ≥ 1**: The compiler uses at most `n` local variables when generating Jasmin instructions. If this limit is insufficient for a method, the compiler reports an error indicating the minimum number of required variables.
- **n = 0**: The compiler minimizes the number of local variables used.
- **n = -1**: The compiler preserves the original variable allocation from the OLLIR representation (default behavior).

Our implementation uses the following techniques:

1. **Liveness Analysis**: We perform data-flow analysis to determine the lifetime of variables.
2. **Interference Graph Construction**: We build a graph where nodes represent variables, and edges indicate when two variables are live simultaneously.
3. **Graph Coloring Algorithm**: We apply a greedy coloring algorithm to assign registers, minimizing the total number of registers needed.

### 2. Constant Propagation

Constant propagation identifies local variables with constant values and replaces their uses with the constants directly. This optimization is enabled with the `-o` flag.

For example, the following code:
```
a = 3;
i = 0;
while (i < a) {
  i = i + 1;
}
res = i * a;
```

Is optimized to : 

```
a = 3;
i = 0;
while (i < 3) {  // 'a' replaced with 3
  i = i + 1;
}
res = i * 3;     // 'a' replaced with 3
```

Our implementation handles:
* Variable assignments with constant values
* Propagation through control flow structures (if statements, loops)
* Careful analysis to prevent incorrect propagation in loops

### 3. Constant Folding

Constant folding evaluates constant expressions at compile time instead of runtime. This optimization is also enabled with the `-o` flag.

For example:

```
a = 10 + 20;
b = a * 3;
```

Is folded to :

```
a = 30;         // 10 + 20 evaluated at compile time
b = a * 3;
```

Our implementation handles :
* Arithmetic operations (+, -, *, /)
* Logical operations (&&, !)
* Relational operations (<, >)
* Nested expressions with constant values

### Combined Optimizations:

Our compiler applies these optimizations iteratively until reaching a fixed point (no more changes are detected). This approach allows optimizations to build upon each other, revealing additional optimization opportunities.

For example:

```
a = 5;
b = 20 - a;
c = a + b;
```

After constant propagation :

```
a = 5;
b = 20 - 5;    // 'a' replaced with 5
c = 5 + b;     // 'a' replaced with 5
```

After constant folding:

```
a = 5;
b = 15;        // 20 - 5 evaluated at compile time
c = 5 + b;     // 'a' replaced with 5
```

With another round of constant propagation:

```
a = 5;
b = 15;
c = 5 + 15;    // 'b' replaced with 15
```

With another round of constant folding:

```
a = 5;
b = 15;
c = 20;        // 5 + 15 evaluated at compile time
```

### Short-Circuit Evaluation

Our compiler implements proper short-circuit evaluation for logical AND (`&&`) operations. In Java, the right side of an AND expression is only evaluated if the left side is true. This behavior is preserved in our generated code with conditional branching.

For example, `a && b` is compiled to a structure equivalent to:

```
evaluate a
if a is false, jump to end with result = false
evaluate b
result = b
end:
```

This ensures expressions like `x != null && x.method()` don't cause null pointer exceptions when `x` is null.

## 6. Field Access Optimization

Our compiler optimizes field access by generating appropriate `getfield` and `putfield` instructions in the OLLIR code, distinguishing between local variables and class fields for efficient memory access.

# Authors
- Alvaro Tomas Teixeira Silva Pacheco
- Eduardo Renato Fernandes Barbosa
- Pedro Henrique Pessôa Camargo
