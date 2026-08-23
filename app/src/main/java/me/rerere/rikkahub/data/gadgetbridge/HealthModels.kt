/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */


package me.rerere.rikkahub.data.gadgetbridge

import java.time.LocalDate

data class DailySummary(
    val timestamp: Long,
    val date: LocalDate,
    val steps: Int,
    val hrResting: Int?,
    val hrMax: Int?,
    val hrMin: Int?,
    val hrAvg: Int?,
    val stressAvg: Int?,
    val calories: Int?,
    val spo2Avg: Int?,
)

data class ActivitySample(
    val timestamp: Long,
    val heartRate: Int?,
    val steps: Int?,
    val stress: Int?,
    val spo2: Int?,
    val rawIntensity: Int?,
)

data class SleepSummary(
    val timestamp: Long,      // 入睡时间（毫秒时间戳）
    val wakeupTime: Long,     // 醒来时间（毫秒时间戳）
    val totalDuration: Int,   // 总时长（分钟）
    val deepSleep: Int,       // 深睡时长（分钟）
    val lightSleep: Int,      // 浅睡时长（分钟）
    val remSleep: Int,        // REM时长（分钟）
    val awakeDuration: Int,   // 清醒时长（分钟）
    val isAwake: Boolean,     // 是否只是短暂清醒（IS_AWAKE=1）
) {
    val isNap: Boolean get() = isAwake && deepSleep == 0 && lightSleep == 0 && remSleep == 0
}

data class HealthUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val dbFileExists: Boolean = true,
    val currentHeartRate: Int? = null,
    val dailySummaries7: List<DailySummary> = emptyList(),
    val dailySummaries30: List<DailySummary> = emptyList(),
    val sleepSummaries: List<SleepSummary> = emptyList(),
    val latestSpo2: Int? = null,
    val latestStress: Int? = null,
    val todaySteps: Int = 0,
    val todayCalories: Int? = null,
    val stepsRange: StepsRange = StepsRange.SEVEN_DAYS,
)

/**
 * Gadgetbridge 中配对的智能手表/手环设备。
 * 读取自 Gadgetbridge 自动导出的 DEVICE 表，支持任意厂商设备。
 */
data class WatchDevice(
    val name: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val identifier: String = "",
) {
    val displayName: String
        get() = name.ifBlank { model.ifBlank { identifier.ifBlank { "Unknown Watch" } } }
}

enum class StepsRange {
    SEVEN_DAYS,
    THIRTY_DAYS,
}
