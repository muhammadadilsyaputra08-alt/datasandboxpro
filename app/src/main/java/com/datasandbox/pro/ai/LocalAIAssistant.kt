package com.datasandbox.pro.ai

/**
 * Local AI assistant stub. This is an intentionally tiny implementation to
 * enable initial wiring and tests. The real implementation will use a small
 * local NLP model and pattern_weights database as described in the blueprint.
 */
class LocalAIAssistant {
    fun suggestFormula(columnName: String, sample: List<Double>): String {
        // Very small heuristic: if variance is small suggest AVERAGE, otherwise SUM
        if (sample.isEmpty()) return "=SUM(${columnName})"
        val avg = sample.average()
        return if (avg == 0.0) "=SUM(${columnName})" else "=AVERAGE(${columnName})"
    }
}
