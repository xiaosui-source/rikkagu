/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved SSH host the LLM (or user) can reference by name.
 *
 * Secrets (password, privateKey, passphrase) are stored in plaintext in Room. This is the
 * same posture as the rest of the app's stored credentials (provider API keys, etc.).
 * Encryption-at-rest via Android Keystore would be a future hardening.
 */
@Entity(tableName = "ssh_hosts")
data class SshHostEntity(
    /** Display name; also the lookup key from the LLM. */
    @PrimaryKey val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
    val createdAtMs: Long,
)
