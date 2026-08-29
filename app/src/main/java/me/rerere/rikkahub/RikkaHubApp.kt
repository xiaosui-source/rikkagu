/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import me.rerere.common.android.appTempFolder
import com.whl.quickjs.android.QuickJSLoader
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.plugin.di.pluginModule
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.service.DailySummaryService

import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.CrashHandler
import me.rerere.rikkahub.utils.DatabaseUtil
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

private const val TAG = "LingxiApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"
const val POMODORO_NOTIFICATION_CHANNEL_ID = "pomodoro_timer"
const val MUSIC_PLAYER_NOTIFICATION_CHANNEL_ID = "music_player"
const val DEVICE_EVENT_NOTIFICATION_CHANNEL_ID = "device_event_tracking"
const val VOICE_CALL_NOTIFICATION_CHANNEL_ID = "voice_call"
const val ANNOUNCEMENT_NOTIFICATION_CHANNEL_ID = "announcement"

/** 工作区环境自动安装进度通知 ID */
const val WORKSPACE_INSTALL_NOTIFICATION_ID = 1001

class LingxiApp : Application() {
    companion object {
        var INSTANCE: LingxiApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        // 注入搜索服务的 Context (百度等引擎读取 CookieManager 会话 cookie 过验证用)
        me.rerere.search.SearchService.appContext = this
        startKoin {
            androidLogger()
            androidContext(this@LingxiApp)
            workManagerFactory()
            modules(appModule, viewModelModule, dataSourceModule, repositoryModule, pluginModule)
        }
        this.createNotificationChannel()

        // 预热 ChatService 单例: 强制在主线程(Application.onCreate 由 Android 保证
        // 在主线程执行, 且先于同一进程内任何 Service/BroadcastReceiver/Activity 回调)
        // 解析并构造 ChatService。原因: ChatService.init 里有 ProcessLifecycleOwner
        // 的 addObserver, 该 API 强制要求主线程; 而 ChatService 是 Koin 普通 single
        // (懒汉式), 默认在"首次注入它的调用者所在线程"构造。进程冷启动后, 若第一个
        // 访问 chatService 的是 TriggerService(它在自己的
        // Dispatchers.IO 协程里首次访问), 就会在后台线程构造并抛
        // "addObserver must be called on the main thread" 崩溃。这里预热后, 任何
        // -> chatService 解析链。
        runCatching { get<ChatService>() }.onFailure { e ->
            android.util.Log.e(TAG, "Failed to pre-warm ChatService singleton", e)
        }

        // set cursor window size to 32MB
        DatabaseUtil.setCursorWindowSize(32 * 1024 * 1024)

        // install crash handler
        CrashHandler.install(this)

        // Init QuickJS native library
        QuickJSLoader.init()

        // delete temp files
        deleteTempFiles()

        // sync upload files to DB
        syncManagedFiles()


        // Start workflow trigger registry (event-driven automation)
        startWorkflowTriggers()

        // Start network change monitor (invalidates SSH DNS cache on WiFi<->cell handoff)
        startNetworkChangeMonitor()

        // Start App Lock guard (intercepts locked apps when opened) if any app is locked
        startAppLockGuardIfEnabled()

        // 内置 AI: 仿人记忆启动维护（原自动创建工作区+自动安装环境的逻辑已按需求移除，
        // 工作区环境不再自动安装，需要时由用户在工作区页面手动触发）
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            runCatching {
                val memoryRepo: me.rerere.rikkahub.data.repository.MemoryRepository = get()
                val engine = me.rerere.rikkahub.data.ai.memory.ombrebrain.OmbreMemoryEngine(memoryRepo)
                engine.dailyTick(me.rerere.rikkahub.data.repository.MemoryRepository.GLOBAL_MEMORY_ID)
                android.util.Log.d(
                    "OmbreBrain",
                    "记忆遗忘曲线维护完成 (dailyTick)"
                )
            }.onFailure { e ->
                android.util.Log.w("OmbreBrain", "dailyTick 失败: ${e.message}")
            }
        }

        // Start aggressive mode (device event AI trigger) if enabled
        startAggressiveModeIfEnabled()

        // Reschedule daily_cron alarm if plugins need it
        rescheduleDailyCronIfEnabled()

        // Diary summary is generated by external memory service.
        // App no longer schedules local diary summary alarms.

        // Increment launch count
        incrementLaunchCount()

        // Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.Auto)
    }

    private fun incrementLaunchCount() {
        get<AppScope>().launch {
            runCatching {
                val store = get<SettingsStore>()
                val current = store.settingsFlowRaw.first()
                store.update(current.copy(launchCount = current.launchCount + 1))
                Log.i(TAG, "incrementLaunchCount: ${store.settingsFlowRaw.first().launchCount}")
            }.onFailure {
                Log.e(TAG, "incrementLaunchCount failed", it)
            }
        }
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun syncManagedFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<FilesManager>().syncFolder()
            }.onFailure {
                Log.e(TAG, "syncManagedFiles failed", it)
            }
        }
    }


    private fun startWorkflowTriggers() {
        runCatching {
            val registry = get<me.rerere.rikkahub.workflow.trigger.TriggerRegistry>()
            val engine = get<me.rerere.rikkahub.workflow.execution.WorkflowEngine>()
            registry.setEngineCallback(engine.triggerCallback)
            registry.start()
        }.onFailure {
            Log.e(TAG, "startWorkflowTriggers failed", it)
        }
    }

    private fun startNetworkChangeMonitor() {
        runCatching {
            me.rerere.rikkahub.utils.NetworkChangeMonitor.start(this)
        }.onFailure {
            Log.e(TAG, "startNetworkChangeMonitor failed", it)
        }
    }

    private fun startAppLockGuardIfEnabled() {
        runCatching {
            me.rerere.rikkahub.data.service.AppLockGuard.init(this)
        }.onFailure {
            Log.e(TAG, "startAppLockGuardIfEnabled failed", it)
        }
    }

    private fun startAggressiveModeIfEnabled() {
        runCatching {
        }.onFailure {
            Log.e(TAG, "startAggressiveModeIfEnabled failed", it)
        }
    }

    private fun rescheduleDailyCronIfEnabled() {
        DailySummaryService.rescheduleIfEnabled(this)
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(getString(R.string.notification_channel_chat_completed))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)

        val chatLiveUpdateChannel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_chat_live_update))
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatLiveUpdateChannel)


        val pomodoroChannel = NotificationChannelCompat
            .Builder(POMODORO_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("番茄钟")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(pomodoroChannel)

        val musicChannel = NotificationChannelCompat
            .Builder(MUSIC_PLAYER_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("音乐播放")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(musicChannel)

        val deviceEventChannel = NotificationChannelCompat
            .Builder(DEVICE_EVENT_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("设备状态同步")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(deviceEventChannel)

        val voiceCallChannel = NotificationChannelCompat
            .Builder(VOICE_CALL_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("语音通话")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(voiceCallChannel)

        val announcementChannel = NotificationChannelCompat
            .Builder(ANNOUNCEMENT_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("公告")
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(announcementChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        get<AppScope>().cancel()
    }
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "AppScope exception", e)
    }
)
