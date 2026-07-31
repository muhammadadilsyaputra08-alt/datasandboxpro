package com.datasandbox.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.datasandbox.pro.viewmodel.SheetViewModel

private val CELL_WIDTH = 96.dp
private val ROW_HEIGHT = 40.dp
private val HEADER_COLOR = Color(0xFFE7ECE8)

@Composable
fun DataGrid(
    viewModel: SheetViewModel,
    selected: Pair<Int, String>?,
    onCellTap: (row: Int, column: String) -> Unit
) {
    val hScroll = rememberScrollState()

    Column(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
        // Header row
        Row {
            GridCellBox(width = 48.dp, isHeader = true) { }
            viewModel.columns.forEach { col ->
                GridCellBox(width = CELL_WIDTH, isHeader = true) {
                    Text(col, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Reading viewModel.version here ties this composition to any edit,
        // so the grid recomposes whenever a cell (or its dependents) changes.
        val recalcTick = viewModel.version

        LazyColumn(Modifier.fillMaxSize()) {
            items(viewModel.rows.toList()) { row ->
                Row {
                    GridCellBox(width = 48.dp, isHeader = true) {
                        Text(row.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    viewModel.columns.forEach { col ->
                        val isSelected = selected == row to col
                        val isError = viewModel.isError(row, col)
                        GridCellBox(
                            width = CELL_WIDTH,
                            selected = isSelected,
                            error = isError,
                            onClick = { onCellTap(row, col) }
                        ) {
                            Text(
                                text = viewModel.valueAt(row, col),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCellBox(
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    selected: Boolean = false,
    error: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val bg = when {
        isHeader -> HEADER_COLOR
        error -> Color(0xFFFDE8E8)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(ROW_HEIGHT)
            .border(0.5.dp, Color(0xFFD5DAD6))
            .background(bg)
            .then(
                if (onClick != null) Modifier.clickableCompat(onClick) else Modifier
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
