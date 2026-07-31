package com.datasandbox.pro.core

/**
 * Simple evaluator for the tiny formula AST produced by FormulaParser.
 *
 * EvaluationContext currently supports a map of named scalar values. Table
 * and range support are intentionally minimal for the initial implementation.
 */

data class EvaluationContext(
    val values: Map<String, Double> = emptyMap(),
    val tables: Map<String, List<Map<String, String>>> = emptyMap()
)

object FormulaEvaluator {
    fun evaluate(expr: Any, context: EvaluationContext = EvaluationContext()): Double {
        // expr can be Expr or a raw string/number
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
            is Expr.Variable -> ctx.values[e.name] ?: 0.0
            is Expr.UnaryMinus -> -evalExpr(e.inner, ctx)
            is Expr.FunctionCall -> evalFunction(e.name, e.args, ctx)
        }
    }

    private fun evalFunction(name: String, args: List<Expr>, ctx: EvaluationContext): Double {
        val evaluatedArgs = args.map { evalExpr(it, ctx) }
        return when (name.uppercase()) {
            "SUM" -> evaluatedArgs.sum()
            "AVERAGE" -> if (evaluatedArgs.isEmpty()) 0.0 else evaluatedArgs.average()
            "IF" -> {
                // IF(cond, then, else)
                val cond = evaluatedArgs.getOrNull(0) ?: 0.0
                val t = evaluatedArgs.getOrNull(1) ?: 0.0
                val f = evaluatedArgs.getOrNull(2) ?: 0.0
                if (cond != 0.0) t else f
            }
            "PMT" -> {
                // PMT(rate, nper, pv, [fv])
                val rate = evaluatedArgs.getOrNull(0) ?: 0.0
                val nper = evaluatedArgs.getOrNull(1)?.toInt() ?: 0
                val pv = evaluatedArgs.getOrNull(2) ?: 0.0
                val fv = evaluatedArgs.getOrNull(3) ?: 0.0
                FormulaEngine.pmt(rate, nper, pv, fv)
            }
            "VLOOKUP" -> {
                // Very small VLOOKUP(lookupValue, tableName, colIndex, exactMatch)
                // For now, support tableName as a placeholder via ctx.tables
                // We accept lookupValue as number; we search first column of table for string match.
                if (args.size >= 2) {
                    val lookupRaw = args[0]
                    val tableExpr = args[1]
                    // tableExpr might be a variable with table name; try to resolve
                    val tableName = when (tableExpr) {
                        is Expr.Variable -> tableExpr.name
                        else -> null
                    }
                    if (tableName != null) {
                        val tbl = ctx.tables[tableName]
                        val lookupStr = evalExpr(lookupRaw, ctx).toString()
                        if (tbl != null && tbl.isNotEmpty()) {
                            val row = tbl.firstOrNull { it.values.firstOrNull()?.value == lookupStr }
                            val colIndex = evaluatedArgs.getOrNull(2)?.toInt() ?: 1
                            if (row != null) {
                                val valStr = row.values.elementAtOrNull(colIndex - 1)?.value ?: "0"
                                return valStr.toDoubleOrNull() ?: 0.0
                            }
                        }
                    }
                }
                0.0
            }
            else -> 0.0
        }
    }
}
