package com.nxteam.nxstore.data.izzy

import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.Source
import com.nxteam.nxstore.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object IzzySource {
    private const val SITE = "https://apt.izzysoft.de"
    private const val INDEX = "$SITE/fdroid/index"

    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val sizeRegex = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(KB|MB|GB|KiB|MiB|GiB)", RegexOption.IGNORE_CASE)

    suspend fun search(query: String, limit: Int = 25): List<AppItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "$INDEX/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        val html = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext emptyList()
        parseList(Jsoup.parse(html, SITE), limit)
    }

    private fun parseList(doc: Document, limit: Int): List<AppItem> {
        val out = LinkedHashMap<String, AppItem>()
        for (link in doc.select("a[href*='/fdroid/index/apk/']")) {
            if (out.size >= limit) break
            val pkg = extractPackage(link.attr("href")) ?: continue
            if (out.containsKey(pkg)) continue

            val name = (
                link.selectFirst("h4, h3, .appName")?.text()
                    ?: link.selectFirst("img")?.attr("alt")
                    ?: link.text()
                ).orEmpty().trim().takeIf { it.isNotBlank() && it.length <= 80 } ?: continue

            val icon = link.selectFirst("img")?.let { img ->
                img.absUrl("src").ifBlank { img.absUrl("data-src") }
            }.orEmpty()

            val summary = link.parent()?.selectFirst(".appSummary, .summary")?.text()?.trim().orEmpty()

            out[pkg] = AppItem(
                packageName = pkg,
                name = name,
                summary = summary,
                iconUrl = icon,
                source = Source.IZZY,
                storeUrl = "$INDEX/apk/$pkg"
            )
        }
        return out.values.toList()
    }

    private fun extractPackage(href: String): String? {
        val marker = "/fdroid/index/apk/"
        val start = href.indexOf(marker)
        if (start < 0) return null
        val candidate = href.substring(start + marker.length)
            .trim('/')
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (candidate.isBlank() || !candidate.contains('.')) return null
        return if (packageRegex.matches(candidate)) candidate else null
    }

    suspend fun details(pkg: String): AppItem? = withContext(Dispatchers.IO) {
        val url = "$INDEX/apk/$pkg"
        val html = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext null
        val doc = Jsoup.parse(html, SITE)

        val name = doc.selectFirst("h1, h2, .appName")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        val summary = doc.selectFirst(".appSummary, .summary")?.text()?.trim().orEmpty()
        val descriptionHtml = doc.selectFirst(".appDesc, .description, #desc")?.html().orEmpty()
        val icon = doc.selectFirst("img.appIcon, img[src*='/fdroid/repo/icons']")?.let { absImage(it) }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()

        val downloadUrl = doc.select("a[href$=.apk]")
            .map { it.absUrl("href") }
            .firstOrNull { href ->
                val file = href.substringAfterLast('/').substringBefore('?')
                file.startsWith("${pkg}_") || file.startsWith("$pkg-") || file == "$pkg.apk"
            }

        val screenshots = doc.select(".appScreenshot img, .screenshots img")
            .mapNotNull { absImage(it).takeIf { s -> s.isNotBlank() } }
            .take(12)

        AppItem(
            packageName = pkg,
            name = name.ifBlank { pkg },
            summary = summary,
            description = descriptionHtml,
            iconUrl = icon,
            versionName = extractVersion(doc),
            sizeBytes = parseSize(doc.text()),
            isPaid = false,
            source = Source.IZZY,
            downloadUrl = downloadUrl,
            storeUrl = url,
            screenshots = screenshots,
            enriched = true
        )
    }

    private fun extractVersion(doc: Document): String {
        val text = doc.select("a[href$=.apk]").firstOrNull()?.absUrl("href").orEmpty()
        val file = text.substringAfterLast('/').substringBefore(".apk")
        return file.substringAfterLast('_', "").trim()
    }

    private fun absImage(img: Element): String =
        img.absUrl("src").ifBlank { img.absUrl("data-src") }

    private fun parseSize(text: String): Long? {
        val match = sizeRegex.find(text) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase()) {
            "KB", "KIB" -> 1024L
            "MB", "MIB" -> 1024L * 1024
            "GB", "GIB" -> 1024L * 1024 * 1024
            else -> 1L
        }
        return (value * multiplier).toLong()
    }
}
