package com.datasandbox.pro.core

/**
 * Minimal FormulaEngine scaffold.
 * This is an initial implementation intended as a starting point for
 * integrating a full formula parser/evaluator (ANTLR or custom).
 *
 * Current responsibilities:
 *  - evaluate(formula, context): placeholder returning a string
 *  - pmt(...) : basic PMT financial function
 */
object FormulaEngine {
    fun evaluate(formula: String, context: Map<String, Double> = emptyMap()): String {
        // TODO: Replace with proper formula parser + AST evaluator
        return "<not-implemented: $formula>"
    }

    /**
     * PMT calculation compatible with spreadsheet semantics.
     * rate: periodic interest rate (e.g., monthly rate)
     * nper: number of periods
     * pv: present value (loan principal)
     * fv: future value (defaults to 0)
     * Returns payment per period (negative = outflow)
     */
    fun pmt(rate: Double, nper: Int, pv: Double, fv: Double = 0.0): Double {
        if (nper <= 0) return 0.0
        if (rate == 0.0) return -(pv + fv) / nper.toDouble()
        val r = rate
        val pvif = Math.pow(1.0 + r, nper.toDouble())
        return -(r * (pv * pvif + fv) / (pvif - 1.0))
    }
}
