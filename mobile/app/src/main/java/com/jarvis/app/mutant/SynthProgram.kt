package com.jarvis.app.mutant

/**
 * The `synth` program language — the runtime-interpretable target an LLM
 * SynthesisProvider emits candidate code in (§5: on a phone there is no
 * embedded Kotlin compiler, so generated candidates are structured,
 * runtime-interpretable specifications executed for real).
 *
 * A program is a list of assignments over pure expressions:
 *
 * ```
 * # capability: word_count
 * words = split(trim(text), " ")
 * result = size(words)
 * TEST two_words | input text="hello world" | expect result=2
 * TEST missing | input | error undefined variable 'text'
 * ```
 *
 * Every line is `name = expression`. The final value must be bound to
 * `result`. Evaluation is loud: any undefined variable, unknown builtin or
 * type mismatch becomes [SpecOutput.Failure] — never a silent wrong answer.
 *
 * The language is deliberately tiny (one expression grammar + pure builtins)
 * but NOT closed over a fixed operation list: any composition of its
 * builtins/operators is a generatable capability. This file contains no
 * knowledge of clamp/normalize/invert/reverse_words/lookup.
 */
class SynthProgram private constructor(
    /** Capability comment from the program header ("" when absent). */
    val capability: String,
    /** Canonical program text (assignments only, tests stripped). */
    val source: String,
    private val statements: List<Statement>,
    /** The generated test specification (§12) carried by the same response. */
    val tests: List<TestCase>
) {

    data class Statement(val name: String, val expr: Expr)

    /** Execute against an input map; loud Failure on every error path. */
    fun execute(input: Map<String, Any>): SpecOutput = try {
        val env = HashMap<String, Any>(input.size + statements.size)
        for ((k, v) in input) {
            env[k] = if (v is Number) v.toDouble() else v
        }
        var last: Any? = null
        for ((index, stmt) in statements.withIndex()) {
            last = Interpreter.eval(stmt.expr, Env(env))
            env[stmt.name] = last ?: throw ProgramError("variable '${stmt.name}' was assigned nothing")
        }
        val result = env[RESULT]
            ?: return SpecOutput.Failure("no 'result' assigned")
        SpecOutput.Success(mapOf(RESULT to result))
    } catch (e: ProgramError) {
        SpecOutput.Failure(e.message ?: "program error")
    } catch (t: Throwable) {
        SpecOutput.Failure("spec crashed: ${t.message ?: t.javaClass.simpleName}")
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    companion object {
        const val RESULT = "result"

        /**
         * Parse a model response: extracts the ```synth fenced block (or
         * falls back to treating the whole text as a bare program), splits
         * assignments from `TEST ...` lines and parses both. Throws
         * [IllegalArgumentException] loudly when no usable program exists.
         */
        fun parse(responseText: String): SynthProgram {
            val body = extractFence(responseText)
                ?: throw IllegalArgumentException(
                    "no ```synth program block found in model response"
                )
            return parseBody(body)
        }

        internal fun parseBody(body: String): SynthProgram {
            var capability = ""
            val stmts = mutableListOf<Statement>()
            val testLines = mutableListOf<String>()
            val sourceLines = mutableListOf<String>()

            for (rawLine in body.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    val cap = capabilityComment(line)
                    if (cap != null && capability.isEmpty()) capability = cap
                    continue
                }
                if (line.startsWith("TEST ", ignoreCase = true)) {
                    testLines += line
                    continue
                }
                val eq = line.indexOf('=')
                if (eq <= 0 || !isIdent(line.substring(0, eq).trim())) {
                    throw IllegalArgumentException("synth syntax error, expected 'name = expression': $line")
                }
                val name = line.substring(0, eq).trim()
                val exprSrc = line.substring(eq + 1).trim()
                if (exprSrc.isEmpty()) {
                    throw IllegalArgumentException("synth syntax error, empty expression for '$name'")
                }
                stmts += Statement(name, Parser(exprSrc).parseExpression())
                sourceLines += "$name = $exprSrc"
            }
            require(stmts.isNotEmpty()) { "synth program has no assignments" }

            val tests = testLines.mapIndexed { i, line -> parseTest(line, i) }
            return SynthProgram(capability, sourceLines.joinToString("\n"), stmts, tests)
        }

        private fun extractFence(text: String): String? {
            // Closing fence optional: stop-sequences may strip the trailing ```.
            val marker = Regex("```\\s*synth\\s*\\n(.*?)(```|$)", RegexOption.DOT_MATCHES_ALL)
            marker.find(text)?.let { return it.groupValues[1] }
            // Fallback: an unfenced response whose lines already look like a program.
            val looksLikeProgram = text.lineSequence().any {
                val l = it.trim()
                (l.contains('=') && !l.startsWith("TEST")) || l.startsWith("TEST", true)
            }
            return if (looksLikeProgram) text else null
        }

        private fun capabilityComment(line: String): String? {
            val m = Regex("#\\s*capability:\\s*(.+)").find(line) ?: return null
            return m.groupValues[1].trim().lowercase()
        }

        private fun isIdent(s: String) = s.isNotEmpty() &&
            s[0].isLetter() && s.all { it.isLetterOrDigit() || it == '_' }

        /**
         * `TEST name | input k=v k2="spaced v" | expect result=42`
         * `TEST name | input | error <expected failure substring>`
         */
        internal fun parseTest(line: String, index: Int): TestCase {
            val segments = line.split('|').map { it.trim() }
            val header = segments.getOrNull(0).orEmpty()
            val name = header.removePrefix("TEST ").trim().ifBlank { "test_$index" }
            if (!header.startsWith("TEST", ignoreCase = true)) {
                throw IllegalArgumentException("bad TEST header: $line")
            }
            var input: Map<String, Any> = emptyMap()
            var expected: Map<String, Any> = emptyMap()
            var expectedError: String? = null
            for (seg in segments.drop(1)) {
                when {
                    seg.equals("input", ignoreCase = true) -> input = emptyMap()
                    seg.startsWith("input", ignoreCase = true) ->
                        input = parseAssignments(seg.removePrefix("input").trim(), line)
                    seg.startsWith("expect", ignoreCase = true) ->
                        expected = parseAssignments(seg.removePrefix("expect").trim(), line)
                    seg.startsWith("error", ignoreCase = true) ->
                        expectedError = seg.removePrefix("error").trim()
                    else -> throw IllegalArgumentException("bad TEST segment '$seg' in: $line")
                }
            }
            return TestCase(
                name = name,
                input = input,
                expected = expected,
                expectedError = expectedError,
                isFailureCase = expectedError != null
            )
        }

        private fun parseAssignments(spec: String, contextLine: String): Map<String, Any> {
            if (spec.isBlank()) return emptyMap()
            val out = LinkedHashMap<String, Any>()
            var i = 0
            while (i < spec.length) {
                val eq = spec.indexOf('=', i)
                if (eq <= 0) throw IllegalArgumentException("bad key=value '$spec' in: $contextLine")
                val key = spec.substring(i, eq).trim()
                if (!isIdent(key)) throw IllegalArgumentException("bad key '$key' in: $contextLine")
                var j = eq + 1
                while (j < spec.length && spec[j] == ' ') j++
                val value: Any
                if (j < spec.length && spec[j] == '"') {
                    val close = spec.indexOf('"', j + 1)
                    if (close < 0) throw IllegalArgumentException("unterminated string in: $contextLine")
                    value = spec.substring(j + 1, close)
                    i = close + 1
                } else {
                    val end = spec.indexOf(' ', j).let { if (it < 0) spec.length else it }
                    val token = spec.substring(j, end)
                    value = when {
                        token == "true" -> true
                        token == "false" -> false
                        else -> token.toDoubleOrNull()
                            ?: throw IllegalArgumentException("non-numeric unquoted value '$token' in: $contextLine")
                    }
                    i = end
                }
                out[key] = value
                while (i < spec.length && spec[i] == ' ') i++
            }
            return out
        }
    }

    /** Loud, typed evaluation error surfaced as [SpecOutput.Failure]. */
    class ProgramError(message: String) : RuntimeException(message)

    // ------------------------------------------------------------------
    // Expression AST
    // ------------------------------------------------------------------

    sealed interface Expr
    data class NumLit(val value: Double) : Expr
    data class StrLit(val value: String) : Expr
    data class BoolLit(val value: Boolean) : Expr
    data class VarRef(val name: String) : Expr
    data class UnaryOp(val op: String, val operand: Expr) : Expr
    data class BinaryOp(val op: String, val left: Expr, val right: Expr) : Expr
    data class Call(val function: String, val args: List<Expr>) : Expr

    // ------------------------------------------------------------------
    // Tokenizer + recursive-descent parser
    // ------------------------------------------------------------------

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseExpression(): Expr {
            val e = parseOr()
            skipWs()
            require(pos >= src.length) { "unexpected trailing input at ${src.substring(pos)}" }
            return e
        }

        private fun skipWs() { while (pos < src.length && src[pos] == ' ') pos++ }
        private fun eof() = pos >= src.length
        private fun peek() = if (eof()) ' ' else src[pos]

        private fun match(vararg ops: String): String? {
            for (op in ops) {
                if (src.startsWith(op, pos)) {
                    // Guard: '<' vs '<=', '=' vs '==', '&' vs '&&', '|' vs '||'.
                    pos += op.length
                    return op
                }
            }
            return null
        }

        private fun parseOr(): Expr {
            var left = parseAnd()
            while (true) {
                skipWs()
                val op = match("||") ?: break
                left = BinaryOp(op, left, parseAnd())
            }
            return left
        }

        private fun parseAnd(): Expr {
            var left = parseCmp()
            while (true) {
                skipWs()
                val op = match("&&") ?: break
                left = BinaryOp(op, left, parseCmp())
            }
            return left
        }

        private fun parseCmp(): Expr {
            var left = parseAdd()
            while (true) {
                skipWs()
                val op = match("==", "!=", "<=", ">=", "<", ">") ?: break
                left = BinaryOp(op, left, parseAdd())
            }
            return left
        }

        private fun parseAdd(): Expr {
            var left = parseMul()
            while (true) {
                skipWs()
                val op = match("+", "-") ?: break
                left = BinaryOp(op, left, parseMul())
            }
            return left
        }

        private fun parseMul(): Expr {
            var left = parsePow()
            while (true) {
                skipWs()
                val op = match("*", "/", "%") ?: break
                left = BinaryOp(op, left, parsePow())
            }
            return left
        }

        private fun parsePow(): Expr {
            val base = parseUnary()
            skipWs()
            return if (match("^") != null) BinaryOp("^", base, parsePow()) else base
        }

        private fun parseUnary(): Expr {
            skipWs()
            match("!").let { if (it != null) return UnaryOp("!", parseUnary()) }
            if (!eof() && peek() == '-' && src.getOrNull(pos + 1) != '=') {
                pos++
                return UnaryOp("-", parseUnary())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Expr {
            skipWs()
            if (eof()) throw ProgramError("unexpected end of expression in: $src")
            val c = peek()
            return when {
                c == '(' -> {
                    pos++
                    val e = parseOr()
                    skipWs()
                    if (peek() != ')') throw ProgramError("missing ')' in: $src")
                    pos++
                    e
                }
                c == '"' -> {
                    val close = src.indexOf('"', pos + 1)
                    if (close < 0) throw ProgramError("unterminated string literal in: $src")
                    val s = src.substring(pos + 1, close)
                    pos = close + 1
                    StrLit(s)
                }
                c.isDigit() -> {
                    val start = pos
                    while (!eof() && (peek().isDigit() || peek() == '.')) pos++
                    src.substring(start, pos).toDoubleOrNull()
                        ?.let { NumLit(it) }
                        ?: throw ProgramError("bad number '${src.substring(start, pos)}'")
                }
                c.isLetter() -> {
                    val start = pos
                    while (!eof() && (peek().isLetterOrDigit() || peek() == '_')) pos++
                    val ident = src.substring(start, pos)
                    skipWs()
                    if (peek() == '(') {
                        pos++
                        val args = mutableListOf<Expr>()
                        skipWs()
                        if (peek() == ')') {
                            pos++
                        } else {
                            while (true) {
                                args += parseOr()
                                skipWs()
                                when (peek()) {
                                    ',' -> { pos++; }
                                    ')' -> { pos++; break }
                                    else -> throw ProgramError("expected ',' or ')' in call to '$ident': $src")
                                }
                            }
                        }
                        Call(ident.lowercase(), args)
                    } else {
                        VarRef(ident)
                    }
                }
                else -> throw ProgramError("unexpected character '$c' in: $src")
            }
        }
    }

    // ------------------------------------------------------------------
    // Evaluator
    // ------------------------------------------------------------------

    private class Env(private val vars: MutableMap<String, Any>) {
        fun lookup(name: String): Any =
            vars[name] ?: throw ProgramError("undefined variable '$name'")
    }

    private object Interpreter {

        fun eval(expr: Expr, env: Env): Any = when (expr) {
            is NumLit -> expr.value
            is StrLit -> expr.value
            is BoolLit -> expr.value
            is VarRef -> env.lookup(expr.name)
            is UnaryOp -> evalUnary(expr, env)
            is BinaryOp -> evalBinary(expr, env)
            is Call -> evalCall(expr, env)
        }

        private fun evalUnary(op: UnaryOp, env: Env): Any {
            val v = eval(op.operand, env)
            return when (op.op) {
                "-" -> num(v, "unary -").unaryMinus()
                "!" -> !bool(v, "!")
                else -> throw ProgramError("unknown operator '${op.op}'")
            }
        }

        private fun evalBinary(op: BinaryOp, env: Env): Any {
            val l = eval(op.left, env)
            val r = eval(op.right, env)
            return when (val o = op.op) {
                "+" -> when {
                    l is Double && r is Double -> l + r
                    l is String || r is String -> str(l) + str(r)
                    l is List<*> && r is List<*> -> l + r
                    else -> throw ProgramError("'+' cannot combine ${typeName(l)} and ${typeName(r)}")
                }
                "-", "*", "/", "%" -> {
                    val a = num(l, o); val b = num(r, o)
                    when (o) {
                        "-" -> a - b
                        "*" -> a * b
                        "/" -> if (b == 0.0) throw ProgramError("division by zero") else a / b
                        else -> if (b == 0.0) throw ProgramError("modulo by zero") else a % b
                    }
                }
                "^" -> Math.pow(num(l, o), num(r, o))
                "==" -> equalsVal(l, r)
                "!=" -> !equalsVal(l, r)
                "<", "<=", ">", ">=" -> compareVals(o, l, r)
                "&&" -> bool(l, o) && bool(r, o)
                "||" -> bool(l, o) || bool(r, o)
                else -> throw ProgramError("unknown operator '$o'")
            }
        }

        private fun evalCall(call: Call, env: Env): Any {
            val args = call.args.map { eval(it, env) }
            fun arg(i: Int): Any =
                args.getOrNull(i) ?: throw ProgramError("${call.function}() needs ${i + 1} argument(s)")
            fun numArg(i: Int) = num(arg(i), call.function)
            fun strArg(i: Int) = str(arg(i), call.function)

            return when (call.function) {
                "min" -> listOf(arg(0), arg(1)).minOf { num(it, "min") }
                "max" -> listOf(arg(0), arg(1)).maxOf { num(it, "max") }
                "abs" -> kotlin.math.abs(numArg(0))
                "round" -> kotlin.math.round(numArg(0)).toLong().toDouble()
                "floor" -> kotlin.math.floor(numArg(0))
                "ceil" -> kotlin.math.ceil(numArg(0))
                "sqrt" -> {
                    val v = numArg(0)
                    if (v < 0) throw ProgramError("sqrt of negative") else kotlin.math.sqrt(v)
                }
                "pow" -> Math.pow(numArg(0), numArg(1))
                "clamp" -> numArg(0).coerceIn(numArg(1), numArg(2))
                "num" -> when (val v = arg(0)) {
                    is Double -> v
                    is String -> v.trim().toDoubleOrNull()
                        ?: throw ProgramError("cannot parse '$v' as number")
                    else -> throw ProgramError("num() cannot convert ${typeName(v)}")
                }
                "str" -> str(arg(0))
                "sum" -> {
                    val list = listArg(arg(0), "sum")
                    if (list.isEmpty()) 0.0 else list.sumOf { num(it, "sum") }
                }
                "size" -> when (val v = arg(0)) {
                    is String -> v.length.toDouble()
                    is List<*> -> v.size.toDouble()
                    else -> throw ProgramError("size() needs string or list, got ${typeName(v)}")
                }
                "split" -> {
                    val parts = strArg(0).split(strArg(1))
                    parts.filter { it.isNotEmpty() }
                }
                "join" -> listArg(arg(0), "join").joinToString(strArg(1)) { str(it) }
                "reverse" -> when (val v = arg(0)) {
                    is String -> v.reversed()
                    is List<*> -> v.reversed()
                    else -> throw ProgramError("reverse() needs string or list, got ${typeName(v)}")
                }
                "upper" -> strArg(0).uppercase()
                "lower" -> strArg(0).lowercase()
                "trim" -> strArg(0).trim()
                "contains" -> when (val hay = arg(0)) {
                    is String -> hay.contains(strArg(1))
                    is List<*> -> hay.any { equalsVal(it!!, arg(1)) }
                    else -> throw ProgramError("contains() needs string or list haystack")
                }
                "replace" -> strArg(0).replace(strArg(1), strArg(2))
                "substring" -> {
                    val s = strArg(0)
                    val start = numArg(1).toInt()
                    val end = if (args.size > 2) numArg(2).toInt() else s.length
                    if (start < 0 || end > s.length || start > end) {
                        throw ProgramError("substring($start,$end) out of bounds for length ${s.length}")
                    }
                    s.substring(start, end)
                }
                "index" -> {
                    val seq = arg(0)
                    val i = numArg(1).toInt()
                    when (seq) {
                        is String -> seq.getOrNull(i)?.toString()
                            ?: throw ProgramError("index $i out of bounds")
                        is List<*> -> seq.getOrNull(i)
                            ?: throw ProgramError("index $i out of bounds")
                        else -> throw ProgramError("index() needs string or list")
                    }
                }
                "if" -> if (bool(arg(0), "if")) arg(1) else arg(2)
                else -> throw ProgramError("unknown builtin '${call.function}'")
            }
        }

        private fun listArg(v: Any, fn: String): List<Any> =
            v as? List<Any> ?: throw ProgramError("$fn() needs a list, got ${typeName(v)}")

        private fun num(v: Any, ctx: String): Double =
            v as? Double ?: throw ProgramError("$ctx expects a number, got ${typeName(v)}")

        private fun bool(v: Any, ctx: String): Boolean =
            v as? Boolean ?: throw ProgramError("$ctx expects a boolean, got ${typeName(v)}")

        private fun str(v: Any, ctx: String? = null): String = when (v) {
            is Double -> formatNum(v)
            is String -> v
            is Boolean -> v.toString()
            is List<*> -> v.joinToString(",") { str(it!!) }
            else -> throw ProgramError("${ctx ?: "str()"} cannot stringify ${typeName(v)}")
        }

        private fun formatNum(d: Double): String =
            if (d == d.toLong().toDouble() && kotlin.math.abs(d) < 1e15) {
                d.toLong().toString()
            } else {
                d.toString()
            }

        private fun typeName(v: Any): String = when (v) {
            is Double -> "number"; is String -> "string"
            is Boolean -> "boolean"; is List<*> -> "list"
            else -> v.javaClass.simpleName
        }

        private fun equalsVal(a: Any, b: Any): Boolean = when {
            a is Double && b is Double -> a == b
            a is String && b is String -> a == b
            a is Boolean && b is Boolean -> a == b
            a is List<*> && b is List<*> ->
                a.size == b.size && a.zip(b).all { (x, y) -> equalsVal(x!!, y!!) }
            else -> false
        }

        private fun compareVals(op: String, l: Any, r: Any): Boolean {
            val cmp = when {
                l is Double && r is Double -> l.compareTo(r)
                l is String && r is String -> l.compareTo(r)
                else -> throw ProgramError("cannot compare ${typeName(l)} with ${typeName(r)}")
            }
            return when (op) {
                "<" -> cmp < 0; "<=" -> cmp <= 0
                ">" -> cmp > 0; ">=" -> cmp >= 0
                else -> throw ProgramError("unknown comparison '$op'")
            }
        }
    }
}
