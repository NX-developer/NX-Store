package com.nxteam.nxstore.data

import com.nxteam.nxstore.data.apkpure.ApkPureSource
import com.nxteam.nxstore.data.fdroid.FDroidSource
import com.nxteam.nxstore.data.play.PlaySource
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object AppRepository {

    suspend fun featured(): List<AppItem> = FDroidSource.featured()

    suspend fun search(query: String): List<AppItem> = coroutineScope {
        val fdroid = async { runCatching { FDroidSource.search(query) }.getOrDefault(emptyList()) }
        val apkpure = async { runCatching { ApkPureSource.search(query) }.getOrDefault(emptyList()) }
        val play = async { runCatching { PlaySource.search(query) }.getOrDefault(emptyList()) }

        val combined = LinkedHashMap<String, AppItem>()
        fun merge(list: List<AppItem>) {
            for (item in list) {
                val existing = combined[item.packageName]
                if (existing == null || rank(item.source) < rank(existing.source)) {
                    combined[item.packageName] = if (existing != null) {
                        item.copy(
                            iconUrl = item.iconUrl.ifBlank { existing.iconUrl },
                            summary = item.summary.ifBlank { existing.summary }
                        )
                    } else item
                }
            }
        }
        merge(fdroid.await())
        merge(apkpure.await())
        merge(play.await())
        combined.values.sortedBy { rank(it.source) }
    }

    private fun rank(source: Source): Int = when (source) {
        Source.FDROID -> 0
        Source.APKPURE -> 1
        Source.PLAY -> 2
    }

    suspend fun details(item: AppItem): AppItem = when (item.source) {
        Source.FDROID -> FDroidSource.byPackage(item.packageName) ?: item
        Source.APKPURE -> ApkPureSource.resolveDownload(item)
        Source.PLAY -> PlaySource.details(item.packageName) ?: item
    }
}
