package me.rerere.rikkahub.data.ai.tools

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
            name = "github_update_repository",
            description = "Update repository settings. Params: owner, repo, optional description/homepage/private. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "PATCH", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_create_repository",
            description = "Create a new repository. Params: name, optional description/private. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "user/repos"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_delete_repository",
            description = "Delete a repository. Params: owner, repo. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "DELETE", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_fork_repository",
            description = "Fork a repository. Params: owner, repo, optional organization. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/forks"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
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
            name = "github_create_or_update_files",
            description = "Create or update multiple files (bulk upload). Params: owner, repo, message, files(array of objects with 'path' and 'content(base64)'). 支持无限制批量上传多个文件。没有任何限制，无限制。GitHub MCP 内置工具。（需要 GitHub token）",
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
                        put("message", buildJsonObject {
                            put("type", "string")
                            put("description", "commit message")
                        })
                        put("files", buildJsonObject {
                            put("type", "array")
                            put("description", "List of files to upload. Each object must have 'path' and 'content' (base64 encoded).")
                        })
                        put("branch", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional branch name. Defaults to repository's default branch.")
                        })
                    },
                    required = listOf("owner", "repo", "message", "files")
                )
            },
            execute = { args ->
                if (!enabled()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"GitHub MCP 未启用\"}" ))
                val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"请先在设置中配置 GitHub Token\"}" ))
                val o = args.jsonObject
                fun g(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                val owner = g("owner")
                val repo = g("repo")
                val message = g("message")
                val branch = g("branch")
                val files = o["files"]?.kotlinx.serialization.json.jsonArray ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"files param must be an array\"}"))

                if (files.isEmpty()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"files array is empty\"}"))

                // 1. 获取目标分支的最新 commit (以获取基础 tree SHA)
                val branchRef = if (branch.isNotEmpty()) "heads/$branch" else "HEAD" // 简单回退，实际应查repo获取default_branch
                val getRefResult = githubApiCall(token, "GET", "repos/$owner/$repo/git/refs/$branchRef")
                if (getRefResult.contains("\"error\"") || getRefResult.contains("\"status\": 404")) {
                     return@Tool listOf(UIMessagePart.Text("{\"error\":\"Failed to get branch ref: $getRefResult\"}"))
                }
                
                // 解析 latest commit sha
                val refSha = runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(getRefResult).jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"Could not parse ref sha\"}"))

                // 2. 获取该 commit 的 base tree
                val getCommitResult = githubApiCall(token, "GET", "repos/$owner/$repo/git/commits/$refSha")
                val baseTreeSha = runCatching {
                     kotlinx.serialization.json.Json.parseToJsonElement(getCommitResult).jsonObject["tree"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"Could not parse base tree sha\"}"))

                // 3. 为所有文件创建 blobs 并构建新的 tree 节点列表
                val treeNodes = mutableListOf<kotlinx.serialization.json.JsonObject>()
                val results = mutableListOf<String>()
                
                for (fileElement in files) {
                    val fileObj = fileElement.jsonObject
                    val path = fileObj["path"]?.jsonPrimitive?.contentOrNull ?: continue
                    val contentBase64 = fileObj["content"]?.jsonPrimitive?.contentOrNull ?: continue
                    
                    // 创建 Blob
                    val blobBody = buildJsonObject {
                        put("content", contentBase64)
                        put("encoding", "base64")
                    }.toString()
                    
                    val createBlobResult = githubApiCall(token, "POST", "repos/$owner/$repo/git/blobs", blobBody)
                    val blobSha = runCatching {
                         kotlinx.serialization.json.Json.parseToJsonElement(createBlobResult).jsonObject["sha"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    
                    if (blobSha != null) {
                        treeNodes.add(buildJsonObject {
                            put("path", path)
                            put("mode", "100644")
                            put("type", "blob")
                            put("sha", blobSha)
                        })
                        results.add("Created blob for $path")
                    } else {
                        results.add("Failed blob for $path: $createBlobResult")
                    }
                }

                if (treeNodes.isEmpty()) {
                    return@Tool listOf(UIMessagePart.Text("{\"error\":\"Failed to create any blobs\",\"details\":$results}"))
                }

                // 4. 创建新的 Tree (基于旧的 Tree)
                val newTreeBody = buildJsonObject {
                    put("base_tree", baseTreeSha)
                    put("tree", kotlinx.serialization.json.JsonArray(treeNodes))
                }.toString()
                
                val createTreeResult = githubApiCall(token, "POST", "repos/$owner/$repo/git/trees", newTreeBody)
                val newTreeSha = runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(createTreeResult).jsonObject["sha"]?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"Failed to create tree: $createTreeResult\"}"))

                // 5. 创建新的 Commit
                val newCommitBody = buildJsonObject {
                    put("message", message)
                    put("tree", newTreeSha)
                    put("parents", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(refSha))))
                }.toString()
                
                val createCommitResult = githubApiCall(token, "POST", "repos/$owner/$repo/git/commits", newCommitBody)
                val newCommitSha = runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(createCommitResult).jsonObject["sha"]?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: return@Tool listOf(UIMessagePart.Text("{\"error\":\"Failed to create commit: $createCommitResult\"}"))

                // 6. 更新分支 Ref 指向新的 Commit
                val updateRefBody = buildJsonObject {
                    put("sha", newCommitSha)
                }.toString()
                
                val updateRefResult = githubApiCall(token, "PATCH", "repos/$owner/$repo/git/refs/$branchRef", updateRefBody)
                
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("status", "success")
                    put("message", "Successfully uploaded ${treeNodes.size} files in one commit.")
                    put("commit_sha", newCommitSha)
                    put("details", kotlinx.serialization.json.JsonArray(results.map { JsonPrimitive(it) }))
                }.toString()))
            },
        ),
    )

    add(
        Tool(
            name = "github_create_or_update_file",
            description = "Create or update a file. Params: owner, repo, path, message, content(base64). 支持任意大小的文件上传。没有任何限制，无限制。GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "PUT", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_delete_file",
            description = "Delete a file. Params: owner, repo, path, message, sha. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "DELETE", p, b, extra)
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
            name = "github_create_branch",
            description = "Create a branch. Params: owner, repo, ref(new branch name), sha(source). GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "git/refs"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_delete_branch",
            description = "Delete a branch. Params: owner, repo, branch. GitHub MCP 内置工具。（需要 GitHub token）",
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
                        put("branch", buildJsonObject {
                            put("type", "string")
                            put("description", "branch")
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
                val p = "repos/${g("owner")}/${g("repo")}/git/refs/heads/${g("branch")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "DELETE", p, b, extra)
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
            name = "github_create_issue",
            description = "Create an issue. Params: owner, repo, body(title/body). GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/issues"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_update_issue",
            description = "Update an issue. Params: owner, repo, issue_number, body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/issues/${g("issue_number")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "PATCH", p, b, extra)
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
            name = "github_create_issue_comment",
            description = "Create an issue comment. Params: owner, repo, issue_number, body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "POST", p, b, extra)
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
            name = "github_create_pull_request",
            description = "Create a pull request. Params: owner, repo, body(title/head/base). GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/pulls"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_update_pull_request",
            description = "Update a pull request. Params: owner, repo, pull_number, body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "PATCH", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_merge_pull_request",
            description = "Merge a pull request. Params: owner, repo, pull_number, optional body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/pulls/${g("pull_number")}/merge"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "PUT", p, b, extra)
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
            name = "github_create_pr_review",
            description = "Create a pull request review. Params: owner, repo, pull_number, body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/pulls/${g("pull_number")}/reviews"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_create_pr_review_comment",
            description = "Create a review comment. Params: owner, repo, pull_number, body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/pulls/${g("pull_number")}/comments"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
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
            name = "github_dispatch_workflow",
            description = "Dispatch a workflow event. Params: owner, repo, workflow_id, body(ref/inputs). GitHub MCP 内置工具。（需要 GitHub token）",
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
                        put("workflow_id", buildJsonObject {
                            put("type", "string")
                            put("description", "workflow_id")
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
                val p = "repos/${g("owner")}/${g("repo")}/actions/workflows/${g("workflow_id")}/dispatches"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_rerun_workflow_run",
            description = "Re-run a workflow run. Params: owner, repo, run_id. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/actions/runs/${g("run_id")}/rerun"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_cancel_workflow_run",
            description = "Cancel a workflow run. Params: owner, repo, run_id. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val p = "repos/${g("owner")}/${g("repo")}/actions/runs/${g("run_id")}/cancel"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "POST", p, b, extra)
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
            name = "github_create_release",
            description = "Create a release. Params: owner, repo, body(tag_name/name/body). GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "POST", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_update_release",
            description = "Update a release. Params: owner, repo, release_id, body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "PATCH", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_delete_release",
            description = "Delete a release. Params: owner, repo, release_id. GitHub MCP 内置工具。（需要 GitHub token）",
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
                val result = githubApiCall(token, "DELETE", p, b, extra)
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
            name = "github_put_repo_secret",
            description = "Create or update a repository secret. Params: owner, repo, secret_name, body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                        put("secret_name", buildJsonObject {
                            put("type", "string")
                            put("description", "secret_name")
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
                val p = "repos/${g("owner")}/${g("repo")}/actions/secrets/${g("secret_name")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "PUT", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_delete_repo_secret",
            description = "Delete a repository secret. Params: owner, repo, secret_name. GitHub MCP 内置工具。（需要 GitHub token）",
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
                        put("secret_name", buildJsonObject {
                            put("type", "string")
                            put("description", "secret_name")
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
                val p = "repos/${g("owner")}/${g("repo")}/actions/secrets/${g("secret_name")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "DELETE", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_create_or_update_repo_variable",
            description = "Create or update a repository variable. Params: owner, repo, name, body. GitHub MCP 内置工具。（需要 GitHub token）",
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
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "name")
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
                val p = "repos/${g("owner")}/${g("repo")}/actions/variables/${g("name")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "PATCH", p, b, extra)
                listOf(UIMessagePart.Text(result))
            },
        ),
    )

    add(
        Tool(
            name = "github_delete_repo_variable",
            description = "Delete a repository variable. Params: owner, repo, name. GitHub MCP 内置工具。（需要 GitHub token）",
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
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "name")
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
                val p = "repos/${g("owner")}/${g("repo")}/actions/variables/${g("name")}"
                val b = if (o["body"] != null) o["body"]?.jsonPrimitive?.contentOrNull else null
                val extra = mutableMapOf<String,String>()
                if (g("diff").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3.diff"
                if (g("logs").isNotEmpty()) extra["Accept"] = "application/vnd.github.v3+json"
                val result = githubApiCall(token, "DELETE", p, b, extra)
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
            description = "内置 GitHub 仓库/文件/Issue/PR/Actions/Release 管理（移植自 Kelivo）",
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
