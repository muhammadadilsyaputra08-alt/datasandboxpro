package com.datasandbox.pro.core

/**
 * Evaluation context passed through formula evaluation.
 *
 * - [values]: known cell values keyed by uppercase "COLROW" address (e.g. "A1").
 * - [tables]: named lookup tables (used by VLOOKUP/INDEX/MATCH), where each row
 *   is a Map<columnName, stringValue>.
 *
 * This type was referenced throughout FormulaEngine.kt, FormulaEvaluator.kt, and
 * RecalcEngine.kt but was missing from the archive, causing "Unresolved reference"
 * compile errors.
 */
data class EvaluationContext(
    val values: Map<String, Double> = emptyMap(),
    val tables: Map<String, List<Map<String, String>>> = emptyMap()
) {
    fun getCellValue(name: String): Double {
        return values[name.uppercase()] ?: 0.0
    }

    fun getRangeValues(start: String, end: String): List<Double> {
        val startAddr = parseAddress(start) ?: return listOfNotNull(values[start.uppercase()])
        val endAddr = parseAddress(end) ?: return listOfNotNull(values[end.uppercase()])
        if (startAddr.second != endAddr.second) {
            // Different columns: not a simple vertical range, just return both endpoints.
            return listOf(getCellValue(start), getCellValue(end))
        }
        val col = startAddr.second
        val rows = minOf(startAddr.first, endAddr.first)..maxOf(startAddr.first, endAddr.first)
        return rows.map { row -> values["$col$row"] ?: 0.0 }
    }

    /** Parses e.g. "B12" -> (12, "B"). Returns null if it doesn't look like a cell address. */
    private fun parseAddress(name: String): Pair<Int, String>? {
        val m = Regex("^([A-Z]+)([0-9]+)$").find(name.uppercase()) ?: return null
        val col = m.groupValues[1]
        val row = m.groupValues[2].toIntOrNull() ?: return null
        return row to col
    }
}
