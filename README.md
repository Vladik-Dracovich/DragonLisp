# DragonLisp

**Version 1.7 · Experimental Lisp for the JVM · Java 21 · MIT License**

DragonLisp is a small, independently developed, dynamically typed Lisp implementation written in Java. It provides a REPL, source-file interpreter, exact rational arithmetic, macros, optional runtime type contracts, Java interoperability, and an experimental ahead-of-time compiler that emits real JVM `.class` files using ASM.

> **Project status:** Alpha/experimental. The ASM compiler covers a useful subset of the language and can fall back to interpretation. It is not a production compiler, a Common Lisp implementation, or a secure sandbox. Java 21 and a JDK (not only a JRE) are required.

## Quick start

On EndeavourOS or Arch Linux:

```sh
sudo pacman -S --needed jdk21-openjdk unzip
unzip dragonlisp-1.7.zip
cd dragonlisp
./build.sh
./test-1.7.sh
./run.sh
```

If another Java version is the system default, set `JAVA_HOME` and `PATH` to your JDK 21 installation. Verify with `java -version` and `javac -version`.

On other Linux distributions, install a compatible JDK 21 and run the same scripts. The scripts assume a POSIX shell, `javac`, `jar`, `java`, `javap`, and `diff` on `PATH`.

## Running Lisp code

Interactive REPL:

```sh
./run.sh
```

Interpret a file:

```sh
./build.sh
java -jar build/dragonlisp.jar examples/start.dlisp
```

Example:

```lisp
(defun factorial (n)
  (if (<= n 1)
      1
      (* n (factorial (- n 1)))))

(print (factorial 8))

(defun make-adder (x)
  (lambda (y) (+ x y)))

(print (funcall (make-adder 7) 35))
```

## Direct ASM compilation

Compile a `.dlisp` file to an executable JVM class:

```sh
./compile-asm.sh tests/compiler-1.7.dlisp DragonASM17
java -cp build/classes:build/compiled DragonASM17
```

`compile-asm.sh` first builds DragonLisp itself with `javac`, then invokes the ASM backend. **The user's Lisp source is compiled directly into JVM bytecode; no Java source is generated for that program.** Output is written to `build/compiled/ClassName.class`. The generated class depends on DragonLisp runtime classes from `build/classes` at execution time.

The compiler uses the **internal ASM bundled with OpenJDK 21**, accessed via `--add-exports`. This is an implementation shortcut, not a stable external API. Moving to standalone `org.ow2.asm` is a priority for a future release.

### What is compiled today

The backend directly emits JVM instructions for many literals, symbol reads, `if`, `progn`, `setq`, `defparameter`, `let`, `let*`, `when`, `unless`, short-circuit `and`/`or`, and a set of built-in function calls. It compiles `defun` and `lambda` bodies into JVM methods, including recursive functions, nested lexical closures, captured mutable variables, and zero-argument functions. As of 1.7, compiled closures also support `&rest` and `&body` parameters.

Macros defined earlier in the same source file can be expanded during compilation. Unsupported forms can still be executed by the interpreter. The compiler prints the number of directly emitted nodes, interpreter fallbacks, generated function methods, and macro expansions; those counts are diagnostics for the given input, **not** performance benchmarks or proof of complete language coverage.

### 1.7 compiler/runtime improvements

- **Variadic compiled closures:** `(lambda (x &rest more) ...)`, `(defun f (&rest xs) ...)`, and `&body` are supported by the ASM backend. The rest argument is bound to a Lisp list.
- **Cached JVM method handles:** compiled closures now resolve their generated JVM methods once and invoke cached `MethodHandle` objects rather than using reflective `Method.invoke` for each call.
- **Expanded parity coverage:** new tests compare interpreted and compiled results for empty and nonempty rest arguments, higher-order functions, captured state, and `&body`.
- **GitHub release preparation:** source-only archive, improved documentation, and reproducible release tests.

The compiler still uses boxed dynamic values and runtime environment objects. It does **not** yet provide primitive-specialized arithmetic, direct static call sites for every function call, a complete optimizer, hygienic macros, or a self-contained standalone executable.

## Language tour

### Values and arithmetic

```lisp
(+ 1 2 3)
(* 7 6)
(/ 1 3)           ; exact rational 1/3
(+ 1/2 1/4)       ; exact rational 3/4
(list 1 "two" 3)
```

Numbers include arbitrary-precision integers, decimals, and reduced exact rational values. Arithmetic follows DragonLisp's current numeric rules; it does not promise full Common Lisp numeric compatibility.

### Bindings and control flow

```lisp
(defparameter answer 42)
(let ((x 5) (y 10)) (+ x y))
(let* ((x 5) (y (+ x 10))) y)
(if (> answer 40) "large" "small")
(when (> answer 0) (print answer))
```

`let` evaluates initializers in the parent environment, whereas `let*` evaluates them sequentially in the new environment.

### Closures and rest parameters

```lisp
(defun make-counter (start)
  (lambda (&rest increments)
    (setq start (+ start (reduce (lambda (a b) (+ a b))
                                 (cons 0 increments))))
    start))

(defparameter counter (make-counter 10))
(print (funcall counter 1 2 3)) ; 16
(print (funcall counter 4))     ; 20
```

### Macros

```lisp
(defmacro twice (x) `(+ ,x ,x))
(print (twice 21))
```

Macros are not hygienic. Compiler-side expansion depends on definition order and currently supports only the implemented macro subset.

### Optional types

DragonLisp is **dynamically typed by default**. Type declarations can provide runtime checking in supported contexts; they do not yet generate optimized primitive bytecode.

```lisp
(defun increment (x)
  (declare (type integer x))
  (+ x 1))
```

Check the `tests/` directory for the exact supported type-contract behavior and failure cases.

### Java interoperability

```lisp
(defparameter sb (java/new "java.lang.StringBuilder"))
(java/call sb "append" "Dragon")
(java/call sb "append" "Lisp")
(print (java/call sb "toString"))
```

Java reflection interop is powerful and unrestricted: **do not execute untrusted DragonLisp programs**. Reflection overload selection, arrays, and field support are experimental and not equivalent to a complete Java foreign-function interface.

## Tests

```sh
./test.sh            # interpreter foundation and interop tests
./test-compiler.sh   # Java-source bootstrap compiler parity
./test-asm.sh        # initial ASM parity
./test-1.4.sh        # bindings and conditionals
./test-1.5.sh        # functions and macros
./test-1.6.sh        # lexical closures
./test-1.7.sh        # all earlier suites + variadic closure parity
```

`test-1.7.sh` runs the cumulative suite. Tests compare interpreter output with generated-class output using `diff`, and inspect generated class methods with `javap`. Passing parity tests establishes agreement for the covered examples only, not all possible Lisp programs.

## Repository structure

```text
dragonlisp/
├── README.md
├── LICENSE
├── .gitignore
├── build.sh
├── run.sh
├── compile.sh              # older Java-source bootstrap compiler
├── compile-asm.sh          # direct ASM bytecode backend
├── test*.sh
├── src/main/java/org/dragonlisp/
│   ├── Main.java           # reader, interpreter, runtime values, builtins
│   ├── RuntimeSupport.java # runtime ABI and compiled closures
│   ├── ASMCompiler.java    # direct bytecode emitter
│   └── Compiler.java       # earlier bootstrap compiler
├── examples/
└── tests/
```

`build/` is generated locally and is excluded from the release archive and Git.

## Design and limitations

DragonLisp is intentionally a separate experimental Lisp dialect, not an attempt at full ANSI Common Lisp compliance. Current compiler features are incremental and may change incompatibly. The compiler retains interpreter fallbacks; generated classes require the DragonLisp runtime. Error locations and Java interop diagnostics need more work. The internal JDK ASM dependency limits portability across JDK versions. Macros are unhygienic; declarations are not an optimizing type system; the runtime has no sandbox, package manager, or stable public API.

## Roadmap

1. Adopt the standalone ASM library and add reproducible dependency management.
2. Improve compiled call sites and remove more interpreter fallback cases.
3. Implement overflow-safe primitive numeric specialization and compiler optimizations.
4. Add compiler IR, source spans, structured errors, and larger differential test suites.
5. Add module/package support, tooling, documentation examples, and stable release conventions.

## Contributing and release workflow

Use Java 21. Make changes in a branch, run `./test-1.7.sh`, and include interpreter/compiled parity tests for new compiler features. To publish the source on GitHub, create a repository and upload the **contents of the `dragonlisp/` folder**, preserving directories. Do not upload the generated `build/` directory. Add a release tag such as `v1.7.0` once you've validated the archive on your own machine.

## License

MIT — see [LICENSE](LICENSE).
