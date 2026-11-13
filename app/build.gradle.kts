plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

import java.util.Properties

// 构建号 = git 提交数，装到手机上能分清是哪一版
val buildNumber: Int = providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
    .standardOutput.asText.get().trim().toIntOrNull() ?: 0

// 发布签名：CI 用 Secrets 还原的证书（RUNNEL_KEYSTORE 等环境变量），本机用仓库外的 ~/.android/runnel-release.properties；
// 两边是同一把证书，包可以互相覆盖安装。都没有时退回 debug 证书（别人克隆也能编译）。
val ciKeystore: String? = System.getenv("RUNNEL_KEYSTORE")
val localSigning: Properties? = File(System.getProperty("user.home"), ".android/runnel-release.properties")
    .takeIf { it.isFile }?.let { f -> Properties().apply { f.reader(Charsets.UTF_8).use { load(it) } } }

android {
    namespace = "io.github.xccxcxxc.runnel"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.xccxcxxc.runnel"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // 公开仓库的历史在 2026-10-21 压成了 1 个提交、提交数重新计数；加 200 保证比之前发过的所有包（最大 110）都大，可以覆盖安装
        versionCode = 200 + buildNumber
        versionName = "1.2.4"
        // 仪器测试（app/src/androidTest）跑在模拟器上：.\gradlew.bat :app:connectedDebugAndroidTest
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (ciKeystore != null) {
            create("runnel") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("RUNNEL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RUNNEL_KEY_ALIAS") ?: "runnel"
                keyPassword = System.getenv("RUNNEL_KEYSTORE_PASSWORD")
            }
        } else if (localSigning != null) {
            create("runnel") {
                storeFile = file(localSigning.getProperty("storeFile"))
                storePassword = localSigning.getProperty("storePassword")
                keyAlias = localSigning.getProperty("keyAlias")
                keyPassword = localSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("runnel") ?: signingConfigs.getByName("debug")
        }
        // macrobenchmark / baseline profile 用：与 release 相同的优化，但可被 shell profile
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric 跑 Compose 手势测试（没有真机/模拟器时验证拖动、松手等交互）
        unitTests.isIncludeAndroidResources = true
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.metrics.performance)
    implementation(libs.androidx.browser)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.miuix)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.readability4j)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.espresso.web)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
