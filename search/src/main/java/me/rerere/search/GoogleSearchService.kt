/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import org.jsoup.Jsoup
import java.net.URLEncoder

/** Google - 无需 API Key，直接抓取网页结果（与浏览器搜索一致） */
object GoogleSearchService : SearchService<SearchServiceOptions.GoogleOptions> {
    override val name: String = "Google"

    @Composable
    override fun Description() {
        Text("无需 API Key，直接搜索（与浏览器一致）")
    }

    override fun parameters(options: SearchServiceOptions.GoogleOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.GoogleOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.GoogleOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            // Google 网页版无JS环境会返回 enablejs 页(反爬), 改用官方 Google News RSS:
            // 免Key、无JS、稳定返回结构化结果
            val url = "https://news.google.com/rss/search?q=" +
                URLEncoder.encode(query, "UTF-8") +
                "&hl=zh-CN&gl=CN&ceid=CN:zh-Hans"
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .timeout(10000)
                .get()

            // RSS <item>: title / link / description
            val results = doc.select("item").mapNotNull { item ->
                val title = item.selectFirst("title")?.text()?.ifBlank { null } ?: return@mapNotNull null
                val link = item.selectFirst("link")?.text()?.ifBlank { null } ?: return@mapNotNull null
                val snippet = item.selectFirst("description")?.text()?.take(300) ?: ""
                SearchResultItem(title = title, url = link, text = snippet)
            }

            require(results.isNotEmpty()) { "Search failed: no results found" }
            SearchResult(items = results)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.GoogleOptions
    ): Result<ScrapedResult> = Result.failure(Exception("Scraping is not supported"))
}
