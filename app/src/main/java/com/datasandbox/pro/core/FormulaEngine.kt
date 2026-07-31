package com.datasandbox.pro.core

/**
 * Bridge API used by the rest of the app. Evaluate formulas expressed as strings
 * and return a textual result. This delegates heavy lifting to FormulaEvaluator
 * and the tiny parser.
 */

object FormulaEngine {
    fun evaluate(formula: String, context: Map<String, Double> = emptyMap()): String {
        return try {
            val ctx = EvaluationContext(values = context)
            val value = FormulaEvaluator.evaluate(formula, ctx)
            // Normalize -0.0 to 0.0
            val v = if (value == -0.0) 0.0 else value
            // If integer, show without decimal when it fits
            if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        } catch (ex: Exception) {
            "#ERROR: ${ex.message}"
        }
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
