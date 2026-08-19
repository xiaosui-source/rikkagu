import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "me.rerere.rikkahub"
    compileSdk = 37
    buildToolsVersion = "33.0.2"

    defaultConfig {
        applicationId = "me.rerere.rikkahub"
        minSdk = 26
        targetSdk = 37
        versionCode = 20405
        versionName = "2.4.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_COMMIT", "\"unknown\"")
        buildConfigField("String", "BUILD_TIME", "\"2026-08-05\"")

        // 最快构建: 只打 arm64-v8a (真机主流架构, 原项目 build_install.bat 亦如此)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // libsimple.so 通过 Requery 的 dlopen(nativeLibraryDir + "/libsimple.so")
            // 直接加载, 必须解压到 nativeLibraryDir, 不能只打包在 APK 内
            useLegacyPackaging = true
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

ksp {
    // Room: 提供 schema 目录以支持 AutoMigration 读取旧 schema
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // ===== 内部模块 =====
    implementation(project(":common"))
    implementation(project(":ai"))
    implementation(project(":material3"))
    implementation(project(":web"))
    implementation(project(":search"))
    implementation(project(":highlight"))
    implementation(project(":speech"))
    implementation(project(":workspace"))
    implementation(project(":document"))

    // ===== AndroidX 基础 =====
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.localbroadcastmanager)
    // 本地推理引擎（ONNX Runtime，有 Java API，开箱即用）

    // ===== Compose =====
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation2)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.sonner)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.lucide.icons)
    implementation(libs.huge.icons)
    implementation(libs.reorderable)
    implementation(libs.image.viewer)

    // ===== Room =====
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // ===== Paging / DataStore / WorkManager =====
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // ===== 网络 =====
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // ===== kotlinx =====
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // ===== Koin =====
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.workmanager)

    // ===== 图片 / 媒体 =====
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.coil.cache.control)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // ===== 文档 / 文本 / 工具 =====
    implementation(libs.commons.text)
    implementation(libs.jetbrains.markdown)
    implementation(libs.jsoup)
    implementation(libs.metadata.extractor)
    implementation(libs.diffutils)
    implementation(libs.quickjs)
    implementation(libs.ucrop)
    implementation(libs.pebble)
    implementation(libs.modelcontextprotocol.kotlin.sdk)
    implementation(libs.jmdns)
    implementation(libs.jlatexmath)
    implementation(libs.jlatexmath.font.greek)
    implementation(libs.jlatexmath.font.cyrillic)

    // ===== 识别 / 扫码 / 权限 =====
    implementation(libs.text.recognition)
    implementation(libs.barcode.scanning)
    implementation(libs.image.labeling)
    implementation(libs.quickie.bundled)
    implementation(libs.zxing.core)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.termux.terminal.view)
    implementation(libs.play.services.location)
    implementation(libs.auth0.jwt)
    implementation(libs.cron.utils)
    implementation(libs.jsch)

    // ===== SQLite (FTS 分词扩展, libsimple) =====
    implementation(libs.sqlite.android)

    // ===== 测试 =====
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
