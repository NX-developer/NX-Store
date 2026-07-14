package com.nxteam.nxstore.data

import com.nxteam.nxstore.data.aptoide.AptoideSource
import com.nxteam.nxstore.data.fdroid.FDroidSource
import com.nxteam.nxstore.data.izzy.IzzySource
import com.nxteam.nxstore.data.play.PlaySource
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.SortMode
import com.nxteam.nxstore.model.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object AppRepository {

    private const val ENRICH_LIMIT = 15

    data class HomeFeed(
        val popular: List<AppItem>,
        val openSource: List<AppItem>
    ) {
        val isEmpty: Boolean get() = popular.isEmpty() && openSource.isEmpty()
    }

    suspend fun home(): HomeFeed = coroutineScope {
        val play = async { runCatching { PlaySource.topCharts() }.getOrDefault(emptyList()) }
        val fdroid = async { runCatching { FDroidSource.featured() }.getOrDefault(emptyList()) }
        HomeFeed(popular = play.await(), openSource = fdroid.await())
    }

    suspend fun search(query: String): List<AppItem> = coroutineScope {
        val fdroid = async { runCatching { FDroidSource.search(query) }.getOrDefault(emptyList()) }
        val izzy = async { runCatching { IzzySource.search(query) }.getOrDefault(emptyList()) }
        val aptoide = async { runCatching { AptoideSource.search(query) }.getOrDefault(emptyList()) }
        val play = async { runCatching { PlaySource.search(query) }.getOrDefault(emptyList()) }

        val merged = LinkedHashMap<String, AppItem>()
        listOf(fdroid.await(), izzy.await(), aptoide.await(), play.await()).forEach { list ->
            for (item in list) {
                val existing = merged[item.packageName]
                merged[item.packageName] = if (existing == null) item else combine(existing, item)
            }
        }
        sort(merged.values.toList(), query, SortMode.RELEVANCE)
    }

    suspend fun enrichHome(feed: HomeFeed): HomeFeed =
        feed.copy(popular = enrich(feed.popular))

    suspend fun enrich(items: List<AppItem>): List<AppItem> = coroutineScope {
        val targets = items.take(ENRICH_LIMIT)
        val enriched = targets.map { item ->
            async {
                if (item.enriched || item.source != Source.PLAY) item
                else runCatching { PlaySource.details(item.packageName) }.getOrNull()
                    ?.let { combine(item, it) } ?: item
            }
        }.awaitAll()
        enriched + items.drop(ENRICH_LIMIT)
    }

    private fun combine(base: AppItem, other: AppItem): AppItem {
        val primary = if (rank(base.source) <= rank(other.source)) base else other
        val secondary = if (primary === base) other else base
        val paidHolder = when {
            primary.source == Source.FDROID -> primary
            primary.enriched -> primary
            secondary.enriched -> secondary
            primary.isPaid -> primary
            else -> secondary
        }
        return primary.copy(
            summary = primary.summary.ifBlank { secondary.summary },
            description = primary.description.ifBlank { secondary.description },
            iconUrl = primary.iconUrl.ifBlank { secondary.iconUrl },
            developer = primary.developer.ifBlank { secondary.developer },
            category = primary.category.ifBlank { secondary.category },
            versionName = primary.versionName.ifBlank { secondary.versionName },
            rating = primary.rating ?: secondary.rating,
            ratingCount = primary.ratingCount ?: secondary.ratingCount,
            downloads = primary.downloads ?: secondary.downloads,
            downloadsLabel = primary.downloadsLabel.ifBlank { secondary.downloadsLabel },
            sizeBytes = primary.sizeBytes ?: secondary.sizeBytes,
            isPaid = paidHolder.isPaid,
            priceLabel = paidHolder.priceLabel,
            screenshots = primary.screenshots.ifEmpty { secondary.screenshots },
            videoUrl = primary.videoUrl ?: secondary.videoUrl,
            downloadUrl = primary.downloadUrl ?: secondary.downloadUrl,
            enriched = primary.enriched || secondary.enriched
        )
    }

    fun sort(items: List<AppItem>, query: String, mode: SortMode): List<AppItem> = when (mode) {
        SortMode.RELEVANCE -> items.sortedWith(
            compareBy<AppItem> { relevance(it, query) }
                .thenByDescending { it.downloads ?: -1L }
                .thenByDescending { it.rating ?: -1.0 }
                .thenBy { rank(it.source) }
        )
        SortMode.DOWNLOADS -> items.sortedWith(
            compareByDescending<AppItem> { it.downloads ?: -1L }
                .thenBy { relevance(it, query) }
        )
        SortMode.RATING -> items.sortedWith(
            compareByDescending<AppItem> { it.rating ?: -1.0 }
                .thenByDescending { it.downloads ?: -1L }
                .thenBy { relevance(it, query) }
        )
    }

    private fun relevance(item: AppItem, query: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 9
        val name = item.name.trim().lowercase()
        val pkg = item.packageName.lowercase()
        return when {
            name == q -> 0
            name.startsWith("$q ") || name.startsWith("$q:") || name.startsWith("$q -") -> 1
            name.startsWith(q) -> 2
            name.contains(" $q ") -> 3
            name.contains(q) -> 4
            pkg.contains(q) -> 5
            else -> 6
        }
    }

    private fun rank(source: Source): Int = when (source) {
        Source.FDROID -> 0
        Source.IZZY -> 1
        Source.APTOIDE -> 2
        Source.PLAY -> 3
    }

    suspend fun details(item: AppItem): AppItem = when (item.source) {
        Source.FDROID -> FDroidSource.byPackage(item.packageName)?.let { combine(it, item) } ?: item
        Source.IZZY -> IzzySource.details(item.packageName)?.let { combine(item, it) } ?: item
        Source.APTOIDE -> AptoideSource.resolveDownload(item)
        Source.PLAY -> PlaySource.details(item.packageName)?.let { combine(item, it) } ?: item
    }
}
