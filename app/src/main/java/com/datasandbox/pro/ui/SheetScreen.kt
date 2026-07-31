package com.datasandbox.pro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Formula bar
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
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
                    selected?.let { (row, col) -> viewModel.commitEdit(row, col, formulaText) }
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Commit")
                }
            }

            HorizontalDivider()

            DataGrid(
                viewModel = viewModel,
                selected = selected,
                onCellTap = { row, col -> selected = row to col }
            )
        }
    }
}
