package com.datasandbox.pro.core

/**
 * Formula evaluator updated to support Range AST nodes and to allow functions to
 * receive raw Expr arguments. EvaluationContext exposes cell values keyed by
 * address (e.g. "A1") and helper to expand ranges.
 */

data class EvaluationContext(
    val values: Map<String, Double> = emptyMap(),
    val tables: Map<String, List<Map<String, String>>> = emptyMap()
) {
    fun getCellValue(addr: String): Double = values[addr.uppercase()] ?: 0.0

    fun getRangeValues(start: String, end: String): List<Double> {
        // Support simple ranges like A1:A10 (same column) or A1:C1 (same row)
        val s = start.uppercase()
        val e = end.uppercase()
        val sMatch = Regex("^([A-Z]+)([0-9]+)").find(s)
        val eMatch = Regex("^([A-Z]+)([0-9]+)").find(e)
        if (sMatch == null || eMatch == null) return emptyList()
        val sCol = sMatch.groupValues[1]
        val sRow = sMatch.groupValues[2].toInt()
        val eCol = eMatch.groupValues[1]
        val eRow = eMatch.groupValues[2].toInt()
        val results = mutableListOf<Double>()
        if (sCol == eCol) {
            val from = minOf(sRow, eRow)
            val to = maxOf(sRow, eRow)
            for (r in from..to) {
                val key = sCol + r.toString()
                results.add(getCellValue(key))
            }
        } else if (sRow == eRow) {
            // Row range like A1:C1
            val fromColIndex = colToIndex(sCol)
            val toColIndex = colToIndex(eCol)
            val from = minOf(fromColIndex, toColIndex)
            val to = maxOf(fromColIndex, toColIndex)
            for (c in from..to) {
                val key = indexToCol(c) + sRow.toString()
                results.add(getCellValue(key))
            }
        }
        return results
    }

    private fun colToIndex(col: String): Int {
        var res = 0
        for (ch in col) {
            res = res * 26 + (ch - 'A' + 1)
        }
        return res
    }

    private fun indexToCol(index: Int): String {
        var i = index
        var s = ""
        var n = i
        while (n > 0) {
            val rem = (n - 1) % 26
            s = ('A' + rem) + s
            n = (n - 1) / 26
        }
        return s
    }
}

object FormulaEvaluator {
    fun evaluate(expr: Any, context: EvaluationContext = EvaluationContext()): Double {
        val ast = when (expr) {
            is String -> parseFormula(expr)
            is Expr -> expr
            else -> return 0.0
        }
        return evalExpr(ast, context)
    }

    private fun evalExpr(e: Expr, ctx: EvaluationContext): Double {
        return when (e) {
            is Expr.LiteralNumber -> e.value
            is Expr.Variable -> ctx.getCellValue(e.name)
            is Expr.UnaryMinus -> -evalExpr(e.inner, ctx)
            is Expr.FunctionCall -> evalFunction(e.name, e.args, ctx)
            is Expr.Range -> {
                // Range evaluated as sum by default when used in expression context,
                // but in practice functions like SUM/AVERAGE will explicitly handle ranges.
                // Here we return the sum to provide a deterministic fallback.
                ctx.getRangeValues(e.start, e.end).sum()
            }
        }
    }

    private fun evalFunction(name: String, args: List<Expr>, ctx: EvaluationContext): Double {
        fun flattenArgToValues(arg: Expr): List<Double> {
            return when (arg) {
                is Expr.Range -> ctx.getRangeValues(arg.start, arg.end)
                else -> listOf(evalExpr(arg, ctx))
            }
        }

        when (name.uppercase()) {
            "SUM" -> {
                val vals = args.flatMap { flattenArgToValues(it) }
                return vals.sum()
            }
            "AVERAGE" -> {
                val vals = args.flatMap { flattenArgToValues(it) }
                return if (vals.isEmpty()) 0.0 else vals.average()
            }
            "IF" -> {
                val cond = evalExpr(args.getOrNull(0) ?: Expr.LiteralNumber(0.0), ctx)
                val t = evalExpr(args.getOrNull(1) ?: Expr.LiteralNumber(0.0), ctx)
                val f = evalExpr(args.getOrNull(2) ?: Expr.LiteralNumber(0.0), ctx)
                return if (cond != 0.0) t else f
            }
            "PMT" -> {
                val rate = evalExpr(args.getOrNull(0) ?: Expr.LiteralNumber(0.0), ctx)
                val nper = evalExpr(args.getOrNull(1) ?: Expr.LiteralNumber(0.0), ctx).toInt()
                val pv = evalExpr(args.getOrNull(2) ?: Expr.LiteralNumber(0.0), ctx)
                val fv = evalExpr(args.getOrNull(3) ?: Expr.LiteralNumber(0.0), ctx)
                return FormulaEngine.pmt(rate, nper, pv, fv)
            }
            "VLOOKUP" -> {
                // VLOOKUP(lookupValue, tableName, colIndex, exactMatch)
                // support simple tableName referenced via variable name (not quoted)
                val lookupExpr = args.getOrNull(0)
                val tableExpr = args.getOrNull(1)
                val colIndex = evalExpr(args.getOrNull(2) ?: Expr.LiteralNumber(2.0), ctx).toInt()
                val lookupValue = lookupExpr?.let { evalExpr(it, ctx).toString() } ?: ""
                if (tableExpr is Expr.Variable) {
                    val tblName = tableExpr.name
                    val tbl = ctx.tables[tblName]
                    if (tbl != null) {
                        val row = tbl.firstOrNull { it.values.firstOrNull()?.value == lookupValue }
                        if (row != null) {
                            val valStr = row.values.elementAtOrNull(colIndex - 1)?.value ?: "0"
                            return valStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                }
                return 0.0
            }
        }
        return 0.0
    }
}
