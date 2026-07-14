package com.nxteam.nxstore.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.ui.UiState
import com.nxteam.nxstore.ui.components.AppRow

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpen: (AppItem) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading, UiState.Idle -> CenterBox {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is UiState.Error -> CenterBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
        is UiState.Success -> LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Discover",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                )
            }
            items(s.data, key = { it.id }) { app ->
                AppRow(item = app, onClick = { onOpen(app) })
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
