/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（Version：语义化版本号比较）
 */

package me.rerere.rikkahub.utils

/**
 * 解析后的版本号：core 为数字段，prerelease 为预发布段.
 */
data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<Int>,
)

/**
 * 语义化版本号（value class）.
 *
 * 支持格式：
 *  - 1.2.3
 *  - 1.2.3+build
 *  - 1.2.3-beta1 / 1.2.3-beta.1
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {

    override fun compareTo(other: Version): Int = Version.compareImpl(value, other.value)

    companion object {
        fun compare(a: String, b: String): Int = compareImpl(a, b)

        fun parse(v: String): ParsedVersion {
            // 去掉 + 构建号
            val base = v.split("+").first()
            // 拆分 prerelease（- 后面的部分）
            val dashIdx = base.indexOf('-')
            val (coreStr, preStr) = if (dashIdx >= 0) {
                base.substring(0, dashIdx) to base.substring(dashIdx + 1)
            } else {
                base to ""
            }
            val core = coreStr.split(".").mapNotNull { it.toIntOrNull() }
            val prerelease = if (preStr.isEmpty()) {
                emptyList()
            } else {
                preStr.split(".").mapNotNull { part ->
                    part.filter { it.isDigit() }.toIntOrNull() ?: part.hashCode()
                }
            }
            return ParsedVersion(core, prerelease)
        }

        fun compareImpl(a: String, b: String): Int {
            val pa = parse(a)
            val pb = parse(b)
            val maxLen = maxOf(pa.core.size, pb.core.size)
            for (i in 0 until maxLen) {
                val x = pa.core.getOrElse(i) { 0 }
                val y = pb.core.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            // prerelease：空的更大（正式版 > 预发布版）
            if (pa.prerelease.isEmpty() && pb.prerelease.isNotEmpty()) return 1
            if (pa.prerelease.isNotEmpty() && pb.prerelease.isEmpty()) return -1
            val preLen = maxOf(pa.prerelease.size, pb.prerelease.size)
            for (i in 0 until preLen) {
                val x = pa.prerelease.getOrElse(i) { 0 }
                val y = pb.prerelease.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}

/** 支持 "1.0.0" < Version("2.0.0") 这类 String 与 Version 的混合比较 */
operator fun String.compareTo(other: Version): Int = Version.compareImpl(this, other.value)

/** 支持 Version("2.0.0") > "1.0.0" 这类 Version 与 String 的混合比较 */
operator fun Version.compareTo(other: String): Int = Version.compareImpl(value, other)
