package com.nxteam.nxstore.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.SortMode
import com.nxteam.nxstore.ui.UiState
import com.nxteam.nxstore.ui.components.AppRow

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpen: (AppItem) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Search F-Droid, APKPure, Play") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortMode.entries.forEach { mode ->
                FilterChip(
                    selected = sort == mode,
                    onClick = { viewModel.onSortChange(mode) },
                    label = { Text(mode.label) }
                )
            }
        }

        when (val s = state) {
            UiState.Idle -> Center { Text("Type to search", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            UiState.Loading -> Center { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            is UiState.Error -> Center { Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Center { Text("No results", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(s.data, key = { it.id }) { app ->
                            AppRow(item = app, onClick = { onOpen(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
