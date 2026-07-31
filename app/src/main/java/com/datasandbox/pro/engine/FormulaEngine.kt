package com.datasandbox.pro.engine

import com.datasandbox.pro.model.Cell
import com.datasandbox.pro.model.CellAddress
import com.datasandbox.pro.model.CellValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Resolves a cell's current value / a table's rows for the evaluator. */
interface EvaluationContext {
    val currentTable: String
    fun cellValue(address: CellAddress): CellValue
    fun columnValues(table: String, column: String): List<CellValue>
}

class FormulaEngine {

    /** Parses [formula] (must start with '=') and evaluates it against [context]. */
    fun evaluate(formula: String, context: EvaluationContext): CellValue {
        return try {
            val expr = formula.removePrefix("=")
            val tokens = Lexer(expr).tokenize()
            val ast = Parser(tokens, context.currentTable).parseExpression()
            ast.eval(context)
        } catch (e: CircularReferenceException) {
            CellValue.Err("CIRCULAR")
        } catch (e: Exception) {
            CellValue.Err(e.message ?: "EVAL")
        }
    }

    /** Extracts the cell/range references a formula depends on, for the dependency graph. */
    fun extractDependencies(formula: String, defaultTable: String): List<CellAddress> {
        val expr = formula.removePrefix("=")
        val tokens = Lexer(expr).tokenize()
        val deps = mutableListOf<CellAddress>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t is Token.Ref) {
                val addr = CellAddress.parse(t.text, defaultTable)
                if (addr != null) deps += addr
            }
            i++
        }
        return deps
    }
}

// ---------------------------------------------------------------------
// Lexer
// ---------------------------------------------------------------------

sealed class Token {
    data class Num(val value: Double) : Token()
    data class Str(val value: String) : Token()
    data class Ref(val text: String) : Token()      // A1, Sheet1!B2
    data class Ident(val name: String) : Token()    // function name
    data class Op(val symbol: String) : Token()
    object LParen : Token()
    object RParen : Token()
    object Comma : Token()
    object Colon : Token()
}

class Lexer(private val src: String) {
    private var pos = 0

    fun tokenize(): List<Token> {
        val out = mutableListOf<Token>()
        while (pos < src.length) {
            val c = src[pos]
            when {
                c.isWhitespace() -> pos++
                c == '(' -> { out += Token.LParen; pos++ }
                c == ')' -> { out += Token.RParen; pos++ }
                c == ',' -> { out += Token.Comma; pos++ }
                c == ':' -> { out += Token.Colon; pos++ }
                c == '"' -> out += readString()
                c.isDigit() || (c == '.' && pos + 1 < src.length && src[pos + 1].isDigit()) -> out += readNumber()
                c.isLetter() || c == '_' -> out += readIdentOrRef()
                "+-*/^=<>&".contains(c) -> out += readOperator()
                else -> pos++
            }
        }
        return out
    }

    private fun readString(): Token.Str {
        pos++ // skip opening quote
        val sb = StringBuilder()
        while (pos < src.length && src[pos] != '"') { sb.append(src[pos]); pos++ }
        if (pos < src.length) pos++ // skip closing quote
        return Token.Str(sb.toString())
    }

    private fun readNumber(): Token.Num {
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
        return Token.Num(src.substring(start, pos).toDouble())
    }

    private fun readIdentOrRef(): Token {
        val start = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_' || src[pos] == '!')) pos++
        val text = src.substring(start, pos)
        // Cell reference pattern: optional Sheet!, letters then digits
        return if (Regex("^([A-Za-z0-9_]+!)?[A-Za-z]+\\d+$").matches(text)) {
            Token.Ref(text.uppercase())
        } else {
            Token.Ident(text.uppercase())
        }
    }

    private fun readOperator(): Token.Op {
        // support two-char operators
        val two = if (pos + 1 < src.length) src.substring(pos, pos + 2) else ""
        return if (two in setOf(">=", "<=", "<>")) {
            pos += 2; Token.Op(two)
        } else {
            val one = src[pos].toString(); pos++; Token.Op(one)
        }
    }
}

// ---------------------------------------------------------------------
// AST + recursive-descent Parser (precedence: ^  > * /  > + -  > comparisons)
// ---------------------------------------------------------------------

sealed class Node {
    abstract fun eval(ctx: EvaluationContext): CellValue

    data class Lit(val value: CellValue) : Node() {
        override fun eval(ctx: EvaluationContext) = value
    }

    data class RefNode(val address: CellAddress) : Node() {
        override fun eval(ctx: EvaluationContext): CellValue = ctx.cellValue(address)
    }

    data class RangeNode(val table: String, val startCol: String, val endCol: String) : Node() {
        override fun eval(ctx: EvaluationContext): CellValue {
            // Ranges only meaningful inside functions; direct eval returns first value.
            val vals = resolve(ctx)
            return vals.firstOrNull() ?: CellValue.Empty
        }
        fun resolve(ctx: EvaluationContext): List<CellValue> = ctx.columnValues(table, startCol)
    }

    data class BinOp(val op: String, val left: Node, val right: Node) : Node() {
        override fun eval(ctx: EvaluationContext): CellValue {
            val l = left.eval(ctx)
            val r = right.eval(ctx)
            return when (op) {
                "+" -> CellValue.Num(l.asDouble() + r.asDouble())
                "-" -> CellValue.Num(l.asDouble() - r.asDouble())
                "*" -> CellValue.Num(l.asDouble() * r.asDouble())
                "/" -> {
                    val rd = r.asDouble()
                    if (rd == 0.0) CellValue.Err("DIV/0") else CellValue.Num(l.asDouble() / rd)
                }
                "^" -> CellValue.Num(l.asDouble().pow(r.asDouble()))
                "&" -> CellValue.Str(l.display() + r.display())
                "=" -> CellValue.Bool(l.display() == r.display())
                "<>" -> CellValue.Bool(l.display() != r.display())
                ">" -> CellValue.Bool(l.asDouble() > r.asDouble())
                "<" -> CellValue.Bool(l.asDouble() < r.asDouble())
                ">=" -> CellValue.Bool(l.asDouble() >= r.asDouble())
                "<=" -> CellValue.Bool(l.asDouble() <= r.asDouble())
                else -> CellValue.Err("OP:$op")
            }
        }
    }

    data class UnaryMinus(val node: Node) : Node() {
        override fun eval(ctx: EvaluationContext) = CellValue.Num(-node.eval(ctx).asDouble())
    }

    data class Call(val name: String, val args: List<Node>) : Node() {
        override fun eval(ctx: EvaluationContext): CellValue = FunctionLibrary.call(name, args, ctx)
    }
}

class Parser(private val tokens: List<Token>, private val defaultTable: String) {
    private var pos = 0
    private fun peek(): Token? = tokens.getOrNull(pos)
    private fun advance(): Token = tokens[pos++]

    fun parseExpression(): Node = parseComparison()

    private fun parseComparison(): Node {
        var left = parseConcat()
        while (peek() is Token.Op && (peek() as Token.Op).symbol in setOf("=", "<>", ">", "<", ">=", "<=")) {
            val op = (advance() as Token.Op).symbol
            left = Node.BinOp(op, left, parseConcat())
        }
        return left
    }

    private fun parseConcat(): Node {
        var left = parseAdditive()
        while (peek() is Token.Op && (peek() as Token.Op).symbol == "&") {
            advance()
            left = Node.BinOp("&", left, parseAdditive())
        }
        return left
    }

    private fun parseAdditive(): Node {
        var left = parseMultiplicative()
        while (peek() is Token.Op && (peek() as Token.Op).symbol in setOf("+", "-")) {
            val op = (advance() as Token.Op).symbol
            left = Node.BinOp(op, left, parseMultiplicative())
        }
        return left
    }

    private fun parseMultiplicative(): Node {
        var left = parsePower()
        while (peek() is Token.Op && (peek() as Token.Op).symbol in setOf("*", "/")) {
            val op = (advance() as Token.Op).symbol
            left = Node.BinOp(op, left, parsePower())
        }
        return left
    }

    private fun parsePower(): Node {
        var left = parseUnary()
        while (peek() is Token.Op && (peek() as Token.Op).symbol == "^") {
            advance()
            left = Node.BinOp("^", left, parseUnary())
        }
        return left
    }

    private fun parseUnary(): Node {
        if (peek() is Token.Op && (peek() as Token.Op).symbol == "-") {
            advance()
            return Node.UnaryMinus(parseUnary())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Node {
        return when (val t = peek()) {
            is Token.Num -> { advance(); Node.Lit(CellValue.Num(t.value)) }
            is Token.Str -> { advance(); Node.Lit(CellValue.Str(t.value)) }
            is Token.Ref -> {
                advance()
                val addr = CellAddress.parse(t.text, defaultTable)!!
                // range?
                if (peek() is Token.Colon) {
                    advance()
                    val endTok = advance() as Token.Ref
                    val endAddr = CellAddress.parse(endTok.text, defaultTable)!!
                    Node.RangeNode(addr.table, addr.column, endAddr.column)
                } else {
                    Node.RefNode(addr)
                }
            }
            is Token.Ident -> {
                advance()
                if (peek() is Token.LParen) {
                    advance()
                    val args = mutableListOf<Node>()
                    if (peek() !is Token.RParen) {
                        args += parseExpression()
                        while (peek() is Token.Comma) { advance(); args += parseExpression() }
                    }
                    if (peek() is Token.RParen) advance()
                    Node.Call(t.name, args)
                } else {
                    // bare identifier: treat TRUE/FALSE, else error literal
                    when (t.name) {
                        "TRUE" -> Node.Lit(CellValue.Bool(true))
                        "FALSE" -> Node.Lit(CellValue.Bool(false))
                        else -> Node.Lit(CellValue.Err("NAME?"))
                    }
                }
            }
            is Token.LParen -> {
                advance()
                val inner = parseExpression()
                if (peek() is Token.RParen) advance()
                inner
            }
            else -> Node.Lit(CellValue.Err("PARSE"))
        }
    }
}

// ---------------------------------------------------------------------
// Built-in function library (Excel-compatible subset)
// ---------------------------------------------------------------------

object FunctionLibrary {

    fun call(name: String, args: List<Node>, ctx: EvaluationContext): CellValue {
        fun nums(): List<Double> = flattenArgs(args, ctx).map { it.asDouble() }

        return when (name) {
            "SUM" -> CellValue.Num(nums().sum())
            "AVERAGE" -> nums().let { if (it.isEmpty()) CellValue.Err("DIV/0") else CellValue.Num(it.average()) }
            "COUNT" -> CellValue.Num(nums().size.toDouble())
            "MIN" -> nums().let { if (it.isEmpty()) CellValue.Num(0.0) else CellValue.Num(it.min()) }
            "MAX" -> nums().let { if (it.isEmpty()) CellValue.Num(0.0) else CellValue.Num(it.max()) }
            "MEDIAN" -> nums().sorted().let {
                if (it.isEmpty()) CellValue.Err("DIV/0")
                else CellValue.Num(if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2.0)
            }
            "STDEV" -> nums().let { list ->
                if (list.size < 2) CellValue.Err("DIV/0") else {
                    val mean = list.average()
                    val variance = list.sumOf { (it - mean).pow(2) } / (list.size - 1)
                    CellValue.Num(kotlin.math.sqrt(variance))
                }
            }
            "ROUND" -> {
                val v = args[0].eval(ctx).asDouble()
                val digits = if (args.size > 1) args[1].eval(ctx).asDouble().toInt() else 0
                val factor = 10.0.pow(digits)
                CellValue.Num(kotlin.math.round(v * factor) / factor)
            }
            "ABS" -> CellValue.Num(kotlin.math.abs(args[0].eval(ctx).asDouble()))

            "IF" -> {
                val cond = args[0].eval(ctx)
                val isTrue = (cond as? CellValue.Bool)?.value ?: (cond.asDouble() != 0.0)
                if (isTrue) args[1].eval(ctx) else (args.getOrNull(2)?.eval(ctx) ?: CellValue.Bool(false))
            }
            "AND" -> CellValue.Bool(args.all { boolOf(it.eval(ctx)) })
            "OR" -> CellValue.Bool(args.any { boolOf(it.eval(ctx)) })
            "NOT" -> CellValue.Bool(!boolOf(args[0].eval(ctx)))

            "CONCATENATE", "CONCAT" -> CellValue.Str(args.joinToString("") { it.eval(ctx).display() })
            "LEFT" -> {
                val s = args[0].eval(ctx).display()
                val n = if (args.size > 1) args[1].eval(ctx).asDouble().toInt() else 1
                CellValue.Str(s.take(max(0, n)))
            }
            "RIGHT" -> {
                val s = args[0].eval(ctx).display()
                val n = if (args.size > 1) args[1].eval(ctx).asDouble().toInt() else 1
                CellValue.Str(s.takeLast(max(0, n)))
            }
            "MID" -> {
                val s = args[0].eval(ctx).display()
                val start = args[1].eval(ctx).asDouble().toInt() - 1
                val len = args[2].eval(ctx).asDouble().toInt()
                val from = min(max(0, start), s.length)
                val to = min(s.length, from + max(0, len))
                CellValue.Str(s.substring(from, to))
            }

            // --- Financial: standard amortization formulas ---
            "PMT" -> {
                val rate = args[0].eval(ctx).asDouble()
                val nper = args[1].eval(ctx).asDouble()
                val pv = args[2].eval(ctx).asDouble()
                val fv = if (args.size > 3) args[3].eval(ctx).asDouble() else 0.0
                CellValue.Num(pmt(rate, nper, pv, fv))
            }
            "FV" -> {
                val rate = args[0].eval(ctx).asDouble()
                val nper = args[1].eval(ctx).asDouble()
                val pmtV = args[2].eval(ctx).asDouble()
                val pv = if (args.size > 3) args[3].eval(ctx).asDouble() else 0.0
                CellValue.Num(if (rate == 0.0) -(pv + pmtV * nper) else -(pv * (1 + rate).pow(nper) + pmtV * (((1 + rate).pow(nper) - 1) / rate)))
            }
            "PV" -> {
                val rate = args[0].eval(ctx).asDouble()
                val nper = args[1].eval(ctx).asDouble()
                val pmtV = args[2].eval(ctx).asDouble()
                CellValue.Num(if (rate == 0.0) -(pmtV * nper) else -(pmtV * (1 - (1 + rate).pow(-nper)) / rate))
            }
            "NPER" -> {
                val rate = args[0].eval(ctx).asDouble()
                val pmtV = args[1].eval(ctx).asDouble()
                val pv = args[2].eval(ctx).asDouble()
                CellValue.Num(if (rate == 0.0) -pv / pmtV else kotlin.math.ln((pmtV - rate * -pv) / pmtV) / kotlin.math.ln(1 + rate) * -1.0)
            }
            "NPV" -> {
                val rate = args[0].eval(ctx).asDouble()
                val flows = flattenArgs(args.drop(1), ctx).map { it.asDouble() }
                CellValue.Num(flows.mapIndexed { i, cf -> cf / (1 + rate).pow((i + 1).toDouble()) }.sum())
            }

            else -> CellValue.Err("NAME?:$name")
        }
    }

    /** PMT formula: (pv*rate + fv*rate) / (1 - (1+rate)^-nper), negated for payment convention. */
    private fun pmt(rate: Double, nper: Double, pv: Double, fv: Double): Double {
        return if (rate == 0.0) {
            -(pv + fv) / nper
        } else {
            val factor = (1 + rate).pow(nper)
            -(pv * factor + fv) * rate / (factor - 1)
        }
    }

    private fun boolOf(v: CellValue) = (v as? CellValue.Bool)?.value ?: (v.asDouble() != 0.0)

    private fun flattenArgs(args: List<Node>, ctx: EvaluationContext): List<CellValue> {
        val out = mutableListOf<CellValue>()
        for (a in args) {
            if (a is Node.RangeNode) out += a.resolve(ctx) else out += a.eval(ctx)
        }
        return out.filter { it !is CellValue.Empty }
    }
}
