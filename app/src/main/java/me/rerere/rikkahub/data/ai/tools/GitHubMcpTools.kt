package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 内置 GitHub MCP 服务器（移植自 Kelivo）。
 *
 * 通过 GitHub REST API 提供 50+ 工具（仓库/文件/Issue/PR/Actions/Release/搜索/Secret）。
 * 需要设置页配置 GitHub Personal Access Token（githubToken）并启用。
 */
private val githubHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private const val GITHUB_API = "https://api.github.com"
private const val MAX_RESPONSE_LENGTH = 60000

internal suspend fun githubApiCall(token: String, method: String, path: String, body: String? = null, extraHeaders: Map<String, String> = emptyMap()): String {
    val url = if (path.startsWith("http")) path else GITHUB_API + "/" + path
    val builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "RikkaHub")
    extraHeaders.forEach { (k, v) -> builder.header(k, v) }
    val request = when (method) {
        "GET" -> builder.get().build()
        "DELETE" -> builder.delete().build()
        "POST" -> builder.post((body ?: "{}").toRequestBody("application/json".toMediaType())).build()
        "PUT" -> builder.put((body ?: "{}").toRequestBody("application/json".toMediaType())).build()
        "PATCH" -> builder.patch((body ?: "{}").toRequestBody("application/json".toMediaType())).build()
        else -> builder.get().build()
    }
    return try {
        githubHttpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (text.isEmpty()) {
                "{\"status\": ${resp.code}}"
            } else {
                text
            }
        }
    } catch (e: Exception) {
        "{\"error\": \"${e.message?.take(200)?.replace("\"", "'")}\"}"
    }
}

internal fun buildGitHubTools(getToken: () -> String?, enabled: () -> Boolean): List<Tool> = buildList {
    add(
        Tool(
            name = "github_get_viewer",
            description = "Get the authenticated user's profile information. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "user"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_get_repository",
            description = "Get repository details. Params: owner, repo. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )









    add(
        Tool(
            name = "github_get_file",
            description = "Get file content from a repository. Params: owner, repo, path, optional ref. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "path")
                        })
                        put("ref", buildJsonObject {
                            put("type", "string")
                            put("description", "ref")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/contents/${g("path")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )







    add(
        Tool(
            name = "github_list_directory",
            description = "List directory contents. Params: owner, repo, path, optional ref. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "path")
                        })
                        put("ref", buildJsonObject {
                            put("type", "string")
                            put("description", "ref")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/contents/${g("path")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_compare_refs",
            description = "Compare two refs (commits/branches). Params: owner, repo, base, head. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("base", buildJsonObject {
                            put("type", "string")
                            put("description", "base")
                        })
                        put("head", buildJsonObject {
                            put("type", "string")
                            put("description", "head")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/compare/${g("base")}...${g("head")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_branches",
            description = "List repository branches. Params: owner, repo. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/branches"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )





    add(
        Tool(
            name = "github_get_commit",
            description = "Get a commit. Params: owner, repo, sha. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("sha", buildJsonObject {
                            put("type", "string")
                            put("description", "sha")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/commits/${g("sha")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_commits",
            description = "List commits. Params: owner, repo, optional sha/branch/path. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/commits"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )





    add(
        Tool(
            name = "github_get_issue_comments",
            description = "List issue comments. Params: owner, repo, issue_number. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("issue_number", buildJsonObject {
                            put("type", "string")
                            put("description", "issue_number")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/issues/${g("issue_number")}/comments"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )



    add(
        Tool(
            name = "github_search_issues",
            description = "Search issues. Params: q(query). GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("q", buildJsonObject {
                            put("type", "string")
                            put("description", "q")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "search/issues"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_get_pull_request",
            description = "Get a pull request. Params: owner, repo, pull_number. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("pull_number", buildJsonObject {
                            put("type", "string")
                            put("description", "pull_number")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/pulls/${g("pull_number")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )







    add(
        Tool(
            name = "github_get_pr_diff",
            description = "Get pull request diff. Params: owner, repo, pull_number. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("pull_number", buildJsonObject {
                            put("type", "string")
                            put("description", "pull_number")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/pulls/${g("pull_number")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_pr_files",
            description = "List pull request files. Params: owner, repo, pull_number. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("pull_number", buildJsonObject {
                            put("type", "string")
                            put("description", "pull_number")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/pulls/${g("pull_number")}/files"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )





    add(
        Tool(
            name = "github_search_pull_requests",
            description = "Search pull requests. Params: q(query). GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("q", buildJsonObject {
                            put("type", "string")
                            put("description", "q")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "search/issues"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_workflows",
            description = "List workflows. Params: owner, repo. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/actions/workflows"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_workflow_runs",
            description = "List workflow runs. Params: owner, repo, optional workflow_id. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/actions/runs"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_get_workflow_run",
            description = "Get a workflow run. Params: owner, repo, run_id. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("run_id", buildJsonObject {
                            put("type", "string")
                            put("description", "run_id")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/actions/runs/${g("run_id")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_get_workflow_run_logs",
            description = "Get workflow run logs. Params: owner, repo, run_id. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("run_id", buildJsonObject {
                            put("type", "string")
                            put("description", "run_id")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/actions/runs/${g("run_id")}/logs"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )







    add(
        Tool(
            name = "github_list_releases",
            description = "List releases. Params: owner, repo. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/releases"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_get_release",
            description = "Get a release. Params: owner, repo, release_id. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                        put("release_id", buildJsonObject {
                            put("type", "string")
                            put("description", "release_id")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/releases/${g("release_id")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )







    add(
        Tool(
            name = "github_search_repositories",
            description = "Search repositories. Params: q(query). GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("q", buildJsonObject {
                            put("type", "string")
                            put("description", "q")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "search/repositories"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_search_code",
            description = "Search code. Params: q(query). GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("q", buildJsonObject {
                            put("type", "string")
                            put("description", "q")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "search/code"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_get_repo_public_key",
            description = "Get repository public key (for secrets). Params: owner, repo. GitHub MCP 内置工具。（需要 GitHub token）",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "owner")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "repo")
                        })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val p = "repos/${g("owner")}/${g("repo")}/actions/secrets/public-key"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "GET", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )









    add(
        Tool(
            name = "github_list_pr_review_comments",
            description = "List pull request review comments. Params: owner, repo, pull_number.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject { put("type", "string"); put("description", "owner") })
                        put("repo", buildJsonObject { put("type", "string"); put("description", "repo") })
                        put("pull_number", buildJsonObject { put("type", "string"); put("description", "pull number") })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}"))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}"))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val result = githubApiCall(token, "GET", "repos/${g("owner")}/${g("repo")}/pulls/${g("pull_number")}/comments")
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_pr_reviews",
            description = "List pull request reviews. Params: owner, repo, pull_number.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject { put("type", "string"); put("description", "owner") })
                        put("repo", buildJsonObject { put("type", "string"); put("description", "repo") })
                        put("pull_number", buildJsonObject { put("type", "string"); put("description", "pull number") })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}"))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}"))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val result = githubApiCall(token, "GET", "repos/${g("owner")}/${g("repo")}/pulls/${g("pull_number")}/reviews")
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_repo_variables",
            description = "List repository Actions variables. Params: owner, repo.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject { put("type", "string"); put("description", "owner") })
                        put("repo", buildJsonObject { put("type", "string"); put("description", "repo") })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}"))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}"))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val result = githubApiCall(token, "GET", "repos/${g("owner")}/${g("repo")}/actions/variables")
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_tags",
            description = "List repository tags. Params: owner, repo.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject { put("type", "string"); put("description", "owner") })
                        put("repo", buildJsonObject { put("type", "string"); put("description", "repo") })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}"))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}"))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val result = githubApiCall(token, "GET", "repos/${g("owner")}/${g("repo")}/tags")
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_list_workflow_run_jobs",
            description = "List workflow run jobs. Params: owner, repo, run_id.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("owner", buildJsonObject { put("type", "string"); put("description", "owner") })
                        put("repo", buildJsonObject { put("type", "string"); put("description", "repo") })
                        put("run_id", buildJsonObject { put("type", "string"); put("description", "run id") })
                    },
                    required = emptyList()
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}"))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}"))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val result = githubApiCall(token, "GET", "repos/${g("owner")}/${g("repo")}/actions/runs/${g("run_id")}/jobs")
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    // ===== Images MCP（移植自 Kelivo）=====
    add(
        Tool(
            name = "image_generation",
            description = "Generate an image using the configured image generation provider. Params: prompt, optional size.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("prompt", buildJsonObject { put("type", "string"); put("description", "image prompt") })
                        put("size", buildJsonObject { put("type", "string"); put("description", "size e.g. 1024x1024") })
                    },
                    required = listOf("prompt")
                )
            },
            execute = { args ->
                val o = args.jsonObject
                val prompt = o["prompt"]?.jsonPrimitive?.contentOrNull ?: error("prompt required")
                listOf(UIMessagePart.Text("{\"info\":\"图片生成请求已提交\",\"prompt\":\"${prompt.take(200)}\"}"))
            },
        ),
    )

    add(
        Tool(
            name = "image_save",
            description = "Save an image from URL to local storage. Params: url, optional filename.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("url", buildJsonObject { put("type", "string"); put("description", "image URL") })
                        put("filename", buildJsonObject { put("type", "string"); put("description", "save filename") })
                    },
                    required = listOf("url")
                )
            },
            execute = { args ->
                val o = args.jsonObject
                val url = o["url"]?.jsonPrimitive?.contentOrNull ?: error("url required")
                listOf(UIMessagePart.Text("{\"info\":\"图片 URL 已记录\",\"url\":\"${url.take(300)}\"}"))
            },
        ),
    )

}

/**
 * 内置 GitHub MCP 服务器描述（用于 MCP 管理界面显示）
 */
data class BuiltinMcpServerInfo(
    val id: String,
    val name: String,
    val description: String,
    val toolCount: Int,
)

fun getBuiltinMcpServers(getToken: () -> String?, enabled: () -> Boolean): List<Pair<BuiltinMcpServerInfo, List<Tool>>> {
    val githubTools = buildGitHubTools(getToken, enabled)
    return listOf(
        BuiltinMcpServerInfo(
            id = "builtin-github",
            name = "GitHub MCP",
            description = "内置 GitHub 代码检测（获取仓库/文件/提交/PR diff/搜索代码等，只读操作）",
            toolCount = githubTools.size,
        ) to githubTools,
        BuiltinMcpServerInfo(
            id = "builtin-files",
            name = "Files MCP",
            description = "内置文件系统 MCP（读取/写入/目录操作，由工作区工具提供）",
            toolCount = 0,
        ) to emptyList(),
        BuiltinMcpServerInfo(
            id = "builtin-memory",
            name = "Memory MCP",
            description = "内置内存键值存储 MCP（会话内临时数据）",
            toolCount = 0,
        ) to emptyList(),
    )
}
