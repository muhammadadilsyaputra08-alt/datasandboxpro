package com.datasandbox.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.datasandbox.pro.data.SheetRepository
import com.datasandbox.pro.ui.SheetScreen
import com.datasandbox.pro.ui.theme.DataSandboxTheme
import com.datasandbox.pro.viewmodel.SheetViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DataSandboxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val repository = remember { SheetRepository(applicationContext) }
                    val viewModel = remember { SheetViewModel(repository) }
                    SheetScreen(viewModel)
                }
            }
        }
    }
}
