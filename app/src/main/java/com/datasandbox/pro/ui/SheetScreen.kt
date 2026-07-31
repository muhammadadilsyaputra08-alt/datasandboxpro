package com.datasandbox.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.datasandbox.pro.viewmodel.SheetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScreen(viewModel: SheetViewModel) {
    var selected by remember { mutableStateOf<Pair<Int, String>?>(1 to "A") }
    var formulaText by remember { mutableStateOf("") }

    LaunchedEffect(selected, viewModel.version) {
        selected?.let { (row, col) -> formulaText = viewModel.rawInputAt(row, col) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("DataSandbox Pro") }) }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding).fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Formula bar + grid
            // Formula bar
            var selectedLocal = selected
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = selected?.let { "${it.second}${it.first}" } ?: "",
                        modifier = Modifier.width(56.dp)
                    )
                    OutlinedTextField(
                        value = formulaText,
                        onValueChange = { formulaText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("=SUM(A1:A5) or a value") }
                    )
                    IconButton(onClick = {
                        selected?.let { (row, col) ->
                            viewModel.commitEdit(row, col, formulaText)
                            // move down one row after commit
                            selected = (row + 1) to col
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Commit")
                    }
                }

                HorizontalDivider()

                DataGrid(
                    viewModel = viewModel,
                    selected = selected,
                    onCellTap = { row, col -> selected = row to col },
                    onCellCommit = { row, col, value ->
                        viewModel.commitEdit(row, col, value)
                        // move to next row to emulate spreadsheet enter behavior
                        selected = (row + 1) to col
                    }
                )
            }
        }
    }
}

@Composable
private fun HorizontalDivider() {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
}
