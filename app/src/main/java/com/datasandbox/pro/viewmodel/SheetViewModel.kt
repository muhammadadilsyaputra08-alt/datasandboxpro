package com.datasandbox.pro.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datasandbox.pro.data.SheetRepository
import com.datasandbox.pro.model.CellValue
import kotlinx.coroutines.launch

private const val TABLE = "Sheet1"
private val COLUMNS = listOf("A", "B", "C", "D", "E")
private const val DEFAULT_ROWS = 20

class SheetViewModel(private val repository: SheetRepository) : ViewModel() {

    var version by mutableStateOf(0)
        private set

    val columns: List<String> = COLUMNS
    val rows: IntRange = 1..DEFAULT_ROWS

    init {
        repository.ensureDefaultTable(TABLE, COLUMNS)
    }

    fun valueAt(row: Int, column: String): String {
        val cell = repository.getCell(TABLE, row, column)
        return cell?.value?.display() ?: ""
    }

    fun rawInputAt(row: Int, column: String): String {
        return repository.getCell(TABLE, row, column)?.rawInput ?: ""
    }

    fun isError(row: Int, column: String): Boolean =
        repository.getCell(TABLE, row, column)?.value is CellValue.Err

    fun commitEdit(row: Int, column: String, input: String) {
        viewModelScope.launch {
            repository.setCell(TABLE, row, column, input)
            version++ // trigger recomposition
        }
    }

    override fun onCleared() {
        repository.close()
    }
}
