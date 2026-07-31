package com.datasandbox.pro.core

/**
 * Formula evaluator improvements: better error signalling and VLOOKUP enhancements
 * including optional approximate match behavior for numeric ranges.
 */

// Simple exception types to propagate spreadsheet-style errors
class FormulaError(val code: String, message: String) : Exception(message)

object FormulaEvaluator {
    fun evaluate(expr: Any, context: EvaluationContext = EvaluationContext()): Double {
        val ast = when (expr) {
            is String -> parseFormula(expr)
            is Expr -> expr
            else -> return 0.0
        }
        return try {
            evalExpr(ast, context)
        } catch (e: FormulaError) {
            // propagate as NaN to indicate an error state to caller; caller may map to CellValue.Error
            Double.NaN
        }
    }

    private fun evalExpr(e: Expr, ctx: EvaluationContext): Double {
        return when (e) {
            is Expr.LiteralNumber -> e.value
            is Expr.Variable -> ctx.getCellValue(e.name)
            is Expr.UnaryMinus -> -evalExpr(e.inner, ctx)
            is Expr.FunctionCall -> evalFunction(e.name, e.args, ctx)
            is Expr.Range -> {
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

        fun exprToString(e: Expr): String = when (e) {
            is Expr.Variable -> e.name
            is Expr.LiteralNumber -> e.value.toString()
            else -> evalExpr(e, ctx).toString()
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
            "MATCH" -> {
                val lookup = args.getOrNull(0) ?: return 0.0
                val rangeExpr = args.getOrNull(1)
                val lookupStr = exprToString(lookup)
                if (rangeExpr is Expr.Range) {
                    val vals = ctx.getRangeValues(rangeExpr.start, rangeExpr.end)
                    val idx = vals.indexOfFirst { it.toString() == lookupStr }
                    return if (idx >= 0) (idx + 1).toDouble() else throw FormulaError("#N/A", "No match")
                }
                throw FormulaError("#VALUE!", "Invalid range")
            }
            "INDEX" -> {
                val rangeExpr = args.getOrNull(0)
                val rowIdx = args.getOrNull(1)?.let { evalExpr(it, ctx).toInt() } ?: 1
                if (rangeExpr is Expr.Range) {
                    val vals = ctx.getRangeValues(rangeExpr.start, rangeExpr.end)
                    if (rowIdx in 1..vals.size) return vals[rowIdx - 1]
                    throw FormulaError("#REF!", "Index out of bounds")
                }
                throw FormulaError("#VALUE!", "Invalid range")
            }
            "VLOOKUP" -> {
                val lookupExpr = args.getOrNull(0)
                val tableExpr = args.getOrNull(1)
                val colIndex = args.getOrNull(2)?.let { evalExpr(it, ctx).toInt() } ?: 2
                val exactArg = args.getOrNull(3)?.let { evalExpr(it, ctx) } ?: 1.0
                val exactMatch = exactArg != 0.0
                val lookupValue = lookupExpr?.let { evalExpr(it, ctx).toString() } ?: ""
                if (tableExpr is Expr.Variable) {
                    val tblName = tableExpr.name
                    val tbl = ctx.tables[tblName]
                    if (tbl != null) {
                        // find exact match first
                        val row = tbl.firstOrNull { r -> r.values.firstOrNull() == lookupValue }
                        if (row != null) {
                            val valStr = row.values.elementAtOrNull(colIndex - 1) ?: throw FormulaError("#N/A", "Column missing")
                            return valStr.toDoubleOrNull() ?: throw FormulaError("#VALUE!", "Non-numeric lookup result")
                        }
                        if (!exactMatch) {
                            // approximate match: for numeric lookup, find closest smaller
                            val numeric = lookupValue.toDoubleOrNull()
                            if (numeric != null) {
                                val candidates = tbl.mapNotNull { r -> r.values.firstOrNull()?.toDoubleOrNull()?.let { Pair(it, r) } }
                                val smaller = candidates.filter { it.first <= numeric }.maxByOrNull { it.first }
                                if (smaller != null) {
                                    val valStr = smaller.second.values.elementAtOrNull(colIndex - 1) ?: throw FormulaError("#N/A", "Column missing")
                                    return valStr.toDoubleOrNull() ?: throw FormulaError("#VALUE!", "Non-numeric lookup result")
                                }
                            }
                        }
                        throw FormulaError("#N/A", "No match")
                    }
                }
                throw FormulaError("#REF!", "Table not found")
            }
        }
        throw FormulaError("#NAME?", "Unknown function $name")
    }
}
