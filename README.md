# DragonLisp (JVM)

DragonLisp is a Lisp dialect implemented in Java with a REPL, macros, Java interop, and a JVM bytecode compiler (ASM).
This repo is built as a serious language-tooling portfolio project.

## Status
- [x] Reader/Lexer/Parser + Printer
- [x] REPL (reader-only)
- [ ] Evaluator (special forms, env, closures)
- [ ] Macro system (quasiquote, defmacro)
- [ ] Java interop (reflection + overload resolution)
- [ ] Compiler to JVM bytecode (ASM)
- [ ] Standard library written in DragonLisp

## Run
Requires Java 21+

```bash
./gradlew run
```

## Test
```bash
./gradlew test
```

## REPL
```lisp
dl> (+ 1 2)
(+ 1 2)

dl> [a :b "c"]
[a :b "c"]

dl> {'x 10 :k 20}
{'x 10 :k 20}

dl> 'hello
'hello
```

## Roadmap
See `docs/` (coming soon): language spec, interop rules, bytecode plan.
