# Friday whole-app code tracing

The repository audit runs `.ralph/code_trace.py` against every tracked source file and publishes the result as the `friday-whole-app-code-trace` artifact. The instrumented Android job also passes JUnit/XML and log outputs to the same tracer and publishes `friday-failure-to-source-traces`.

## Run locally

```bash
python3 .ralph/code_trace.py --repo . --out-dir build/friday-code-trace
python3 .ralph/code_trace.py --repo . --out-dir build/friday-code-trace \
  --failure mobile/app/build/test-results/testDebugUnitTest/TEST-example.xml
```

The index covers tracked Kotlin/Java, native, Python, JavaScript-family, shell, Gradle and app configuration/resource files. It includes source paths, declaration line ranges, unique lexical caller/callee edges, ambiguous name matches, syntax errors and mapped failure locations. Use --location path:line or --symbol FunctionName to trace an arbitrary source location or function, not only stack traces. Each failure location is mapped to the smallest containing indexed function when possible; the report then includes its caller and callee neighborhood.

## Repair loop

1. Reproduce the failing behavior and retain the exact test output, stack trace, or runtime report.
2. Map the failure to its source function and inspect its caller/callee neighborhood.
3. Follow the path across service, state, persistence, permission, lifecycle and platform boundaries until the violated behavior is explained.
4. Add or strengthen a regression test that fails before the repair.
5. Make the smallest implementation change that restores the intended behavior.
6. Rerun the reproducer, adjacent tests, full unit suite, Android Lint, build and instrumented tests.
7. Keep unresolved failures visible; do not convert warnings, skipped tests or missing evidence into a pass.

## Accuracy boundary

Python declarations use the Python AST. Kotlin, Java, Swift, Go, Rust, C/C++ and JavaScript-family declarations and calls use a conservative lexical index. Unique lexical name matches are navigation hints, not compiler-proven call edges. Overloads, dynamic dispatch, reflection, dependency injection, callbacks, generated code, JNI/native calls and runtime reachability require compiler, test or device evidence. The tracer does not modify code. It must not be used to justify blind automatic rewrites.
