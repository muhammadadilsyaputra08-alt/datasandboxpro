package com.datasandbox.pro.core

/**
 * Very small recursive-descent parser/tokenizer for spreadsheet-like formulas.
 * Supports numeric literals, parentheses, unary minus, and function calls with comma-separated args.
 *
 * This is intentionally lightweight to avoid adding large dependencies. It is
 * sufficient to evaluate simple formulas such as:
 *  - =SUM(1,2,3)
 *  - =AVERAGE(10,20)
 *  - =PMT(0.01, 12, 10000)
 *  - =IF(1, 10, 20)
 */

private sealed class Token {
    data class Number(val value: Double) : Token()
    data class Ident(val name: String) : Token()
    object LParen : Token()
    object RParen : Token()
    object Comma : Token()
    object Minus : Token()
    object EOF : Token()
}

private class Tokenizer(private val input: String) {
    private var pos = 0
    private val len = input.length

    private fun peek(): Char? = if (pos < len) input[pos] else null
    private fun next(): Char? = if (pos < len) input[pos++] else null

    fun nextToken(): Token {
        while (peek()?.isWhitespace() == true) next()
        val ch = peek() ?: return Token.EOF
        when {
            ch == '(' -> { next(); return Token.LParen }
            ch == ')' -> { next(); return Token.RParen }
            ch == ',' -> { next(); return Token.Comma }
            ch == '-' -> { next(); return Token.Minus }
            ch.isDigit() || ch == '.' -> return readNumber()
            ch.isLetter() -> return readIdent()
            ch == '=' -> { next(); return nextToken() } // skip leading '=' if present
            else -> {
                // skip unknown
                next()
                return nextToken()
            }
        }
    }

    private fun readNumber(): Token.Number {
        val start = pos
        while (peek()?.let { it.isDigit() || it == '.' } == true) next()
        val str = input.substring(start, pos)
        val v = str.toDoubleOrNull() ?: 0.0
        return Token.Number(v)
    }

    private fun readIdent(): Token.Ident {
        val start = pos
        while (peek()?.let { it.isLetterOrDigit() || it == '_' } == true) next()
        val name = input.substring(start, pos)
        return Token.Ident(name.uppercase())
    }
}

private class Parser(private val source: String) {
    private val tokenizer = Tokenizer(source)
    private var lookahead: Token = tokenizer.nextToken()

    private fun eat(): Token {
        val cur = lookahead
        lookahead = tokenizer.nextToken()
        return cur
    }

    fun parseExpression(): Expr {
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        when (val t = lookahead) {
            is Token.Number -> {
                eat()
                return Expr.LiteralNumber(t.value)
            }
            is Token.Minus -> {
                eat()
                val inner = parsePrimary()
                return Expr.UnaryMinus(inner)
            }
            is Token.Ident -> {
                val ident = (eat() as Token.Ident).name
                if (lookahead is Token.LParen) {
                    // function call
                    eat() // (
                    val args = mutableListOf<Expr>()
                    if (lookahead !is Token.RParen) {
                        args.add(parseExpression())
                        while (lookahead is Token.Comma) {
                            eat()
                            args.add(parseExpression())
                        }
                    }
                    if (lookahead is Token.RParen) eat()
                    return Expr.FunctionCall(ident, args)
                }
                return Expr.Variable(ident)
            }
            is Token.LParen -> {
                eat()
                val inner = parseExpression()
                if (lookahead is Token.RParen) eat()
                return inner
            }
            else -> {
                // fallback
                return Expr.LiteralNumber(0.0)
            }
        }
    }
}

// AST nodes
private sealed class Expr {
    data class LiteralNumber(val value: Double) : Expr()
    data class Variable(val name: String) : Expr()
    data class UnaryMinus(val inner: Expr) : Expr()
    data class FunctionCall(val name: String, val args: List<Expr>) : Expr()
}

internal fun parseFormula(formula: String): Expr {
    val p = Parser(formula.trim())
    return p.parseExpression()
}
