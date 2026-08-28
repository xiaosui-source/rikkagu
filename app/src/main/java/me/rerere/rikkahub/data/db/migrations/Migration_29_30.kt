/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 29 → 30：为 memoryentity 表添加 OmbreBrain 仿人记忆字段。
 *
 * 采用"能加则加、已存在则忽略"的防御式写法，兼容部分库已被之前的测试填充过的情况。
 * 老记忆自动获得默认值（importance=0.3, is_active=true, trigger_count=1），保留数据不丢失。
 */
object Migration_29_30 : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val cols = arrayOf(
            "ADD COLUMN title TEXT DEFAULT ''",
            "ADD COLUMN importance REAL NOT NULL DEFAULT 0.3",
            "ADD COLUMN sentiment REAL NOT NULL DEFAULT 0",
            "ADD COLUMN tags TEXT NOT NULL DEFAULT ''",
            "ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0",
            "ADD COLUMN last_triggered_at INTEGER NOT NULL DEFAULT 0",
            "ADD COLUMN trigger_count INTEGER NOT NULL DEFAULT 1",
            "ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1",
            "ADD COLUMN is_habit INTEGER NOT NULL DEFAULT 0",
            "ADD COLUMN source TEXT NOT NULL DEFAULT 'ai'",
            "ADD COLUMN related_ids TEXT NOT NULL DEFAULT ''",
        )
        cols.forEach { col ->
            try {
                db.execSQL("ALTER TABLE memoryentity $col")
            } catch (e: Exception) {
                // 列已存在则忽略
            }
        }
        // 给历史记忆回填时间戳，避免 last_triggered_at=0 被误判为"很久没触发"
        try {
            db.execSQL(
                "UPDATE memoryentity SET created_at = CASE WHEN created_at = 0 THEN strftime('%s','now')*1000 ELSE created_at END, " +
                    "last_triggered_at = CASE WHEN last_triggered_at = 0 THEN strftime('%s','now')*1000 ELSE last_triggered_at END"
            )
        } catch (e: Exception) {
            // 忽略
        }
    }
}