package com.datasandbox.pro.model

/** Column data types supported by a table. */
enum class ColumnType { TEXT, NUMBER, DATE, BOOLEAN, REFERENCE }

data class ColumnDef(
    val id: Long,
    val name: String,
    val type: ColumnType,
    val formula: String? = null,
    val isPrimaryKey: Boolean = false
)

data class TableDef(
    val id: Long,
    val name: String,
    val description: String = "",
    val columns: List<ColumnDef> = emptyList()
)

/** A0, B12, etc. Zero-based internally. */
data class CellAddress(val table: String, val row: Int, val column: String) {
    override fun toString(): String = "$table!$column$row"

    companion object {
        private val REGEX = Regex("^(?:([A-Za-z0-9_]+)!)?([A-Za-z]+)(\\d+)$")

        fun parse(text: String, defaultTable: String): CellAddress? {
            val m = REGEX.matchEntire(text.trim()) ?: return null
            val (tbl, col, row) = m.destructured
            return CellAddress(
                table = tbl.ifEmpty { defaultTable },
                row = row.toInt(),
                column = col.uppercase()
            )
        }
    }
}

sealed class CellValue {
    data class Num(val value: Double) : CellValue()
    data class Str(val value: String) : CellValue()
    data class Bool(val value: Boolean) : CellValue()
    data class Err(val message: String) : CellValue()
    object Empty : CellValue()

    fun asDouble(): Double = when (this) {
        is Num -> value
        is Bool -> if (value) 1.0 else 0.0
        is Str -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    fun display(): String = when (this) {
        is Num -> if (value == value.toLong().toDouble()) value.toLong().toString() else "%.4f".format(value).trimEnd('0').trimEnd('.')
        is Str -> value
        is Bool -> if (value) "TRUE" else "FALSE"
        is Err -> "#ERR: $message"
        Empty -> ""
    }
}

data class Cell(
    val address: CellAddress,
    val rawInput: String = "",
    val formula: String? = null,
    var value: CellValue = CellValue.Empty,
    var dependencies: List<CellAddress> = emptyList()
)
