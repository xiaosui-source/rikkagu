/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.service

import io.ktor.http.HttpStatusCode

sealed class ApiException(
    override val message: String,
    val status: HttpStatusCode
) : RuntimeException(message)

class BadRequestException(message: String) : ApiException(message, HttpStatusCode.BadRequest)
class NotFoundException(message: String) : ApiException(message, HttpStatusCode.NotFound)
class UnauthorizedException(message: String) : ApiException(message, HttpStatusCode.Unauthorized)
class ForbiddenException(message: String) : ApiException(message, HttpStatusCode.Forbidden)
