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

/** 360 搜索 - 无需 API Key，直接抓取网页结果（与浏览器搜索一致） */
object So360SearchService : SearchService<SearchServiceOptions.So360Options> {
    override val name: String = "360"

    @Composable
    override fun Description() {
        Text("无需 API Key，直接搜索（与浏览器一致）")
    }

    override fun parameters(options: SearchServiceOptions.So360Options): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.So360Options): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.So360Options
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://www.so.com/s?q=" + URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", "https://www.so.com/")
                .timeout(8000)
                .get()

            val results = doc.select(".res-list li, .res-list, .result").mapNotNull { element ->
                val titleEl = element.selectFirst("h3 > a, h3 a, .res-title a") ?: return@mapNotNull null
                val title = titleEl.text().ifBlank { return@mapNotNull null }
                val link = titleEl.attr("href")
                    .let { h ->
                        if (h.startsWith("//")) "https:$h"
                        else if (h.startsWith("http")) h
                        else "https://www.so.com$h"
                    }
                val snippet = element.selectFirst(".res-desc, .res-rich, .res-info, .res-linkinfo")?.text() ?: ""
                SearchResultItem(title = title, url = link, text = snippet)
            }

            require(results.isNotEmpty()) { "Search failed: no results found" }
            SearchResult(items = results)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.So360Options
    ): Result<ScrapedResult> = Result.failure(Exception("Scraping is not supported"))
}
