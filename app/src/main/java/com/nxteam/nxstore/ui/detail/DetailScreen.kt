package com.nxteam.nxstore.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.ui.UiState
import com.nxteam.nxstore.ui.components.PriceBadge
import com.nxteam.nxstore.ui.components.SourceBadge
import com.nxteam.nxstore.ui.theme.NxSurfaceVariant
import com.nxteam.nxstore.util.Format
import com.nxteam.nxstore.util.HtmlText

@Composable
fun DetailScreen(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    viewModel: DetailViewModel = viewModel()
) {
    val itemState by viewModel.item.collectAsStateWithLifecycle()
    val installState by viewModel.install.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val s = itemState) {
        is UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is UiState.Success -> {
            val app = s.data
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = app.iconUrl,
                        contentDescription = app.name,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(NxSurfaceVariant)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            app.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (app.developer.isNotBlank()) {
                            Text(
                                app.developer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SourceBadge(app.source)
                            PriceBadge(app)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                ActionButton(
                    app = app,
                    installState = installState,
                    onInstall = { viewModel.install(app) },
                    onOpenStore = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(app.storeUrl))
                            )
                        }
                    }
                )

                val meta = buildList {
                    app.rating?.let { add(String.format(java.util.Locale.US, "★ %.1f", it)) }
                    if (app.downloadsLabel.isNotBlank()) add("${app.downloadsLabel} downloads")
                    if (app.versionName.isNotBlank()) add("v${app.versionName}")
                    Format.size(app.sizeBytes).takeIf { it.isNotBlank() }?.let { add(it) }
                    if (app.category.isNotBlank()) add(app.category)
                }.joinToString("  •  ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                app.videoUrl?.let { video ->
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Watch trailer", color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                if (app.screenshots.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(app.screenshots) { shot ->
                            AsyncImage(
                                model = shot,
                                contentDescription = null,
                                modifier = Modifier
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NxSurfaceVariant)
                            )
                        }
                    }
                }

                if (app.description.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Text("About", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = HtmlText.toAnnotated(app.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(
    app: AppItem,
    installState: InstallState,
    onInstall: () -> Unit,
    onOpenStore: () -> Unit
) {
    when {
        app.isPaid || !app.installable -> {
            Button(
                onClick = onOpenStore,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                val label = if (app.isPaid) "Buy on ${app.source.label} (${app.priceLabel.ifBlank { "Paid" }})"
                else "Open in ${app.source.label}"
                Text(label)
            }
        }
        else -> when (val st = installState) {
            is InstallState.Downloading -> Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { st.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text("Downloading ${(st.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InstallState.Installing -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Installing…") }
            InstallState.AwaitingConfirm -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Confirm the system prompt") }
            is InstallState.Failed -> Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Retry (${st.message})") }
            InstallState.Idle -> Button(onClick = onInstall, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Install") }
        }
    }
}
