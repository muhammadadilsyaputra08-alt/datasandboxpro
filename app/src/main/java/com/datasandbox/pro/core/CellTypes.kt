package com.datasandbox.pro.core

// Core model types used by the engine
sealed class CellValue {
    object Empty : CellValue()
    data class Number(val value: Double) : CellValue()
    data class Text(val value: String) : CellValue()
    data class Bool(val value: Boolean) : CellValue()
    data class Error(val message: String) : CellValue()
}

data class CellAddress(val sheet: String, val row: Int, val column: String)

data class Cell(
    val address: CellAddress,
    val value: CellValue = CellValue.Empty,
    val formula: String? = null,
    val dependencies: List<CellAddress> = emptyList()
)
