import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile
// 注：本文件里**不要**用 `java.util.X` 这种全限定写法 —— Gradle Kotlin DSL 的 `java`
// 是项目扩展访问器（JavaPluginExtension），会把 `java.util` 解析成扩展上的属性 ✗
// ⇒ 必须像上面这样显式 import 后用短名 ✓

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ─────────────────────────────────────────────────────────────────────────────
// 轮19.147：**NDK 版本自适应**
//   · CI（ubuntu-latest）自带 NDK：27.3.13750724 / 28.2.13676358 / 29.0.14206865
//   · 开发机上装的是：28.0.13004108 / 29.0.14206865
//   ⇒ 两边都**没有**任何一个是"共同写死值"能覆盖的（28.0 与 28.2 互不相同 ✗），
//     写死任一个都会让另一边去 dl.google.com 下 ~1GB 的 NDK（本机网络还不一定放行 ✗）。
//   ⇒ 规则：**用当前机器已装的最高版本**；一个都没装才退回写死值（交给 AGP 自行下载 ✓）。
//     两边的"最高版本"恰好都是 29.0.14206865 ✓ ⇒ 本地与 CI 工具链一致 ✓
// ─────────────────────────────────────────────────────────────────────────────
val sdkPath: String? = System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }
    ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }
    ?: runCatching {
        val f = rootProject.file("local.properties")
        if (!f.exists()) return@runCatching null
        val props = Properties()
        f.inputStream().use { props.load(it) }
        props.getProperty("sdk.dir")
    }.getOrNull()

val installedNdkVersions: List<String> = sdkPath
    ?.let { runCatching { File(it, "ndk").listFiles()?.filter { d -> d.isDirectory }?.map { d -> d.name } }.getOrNull() }
    .orEmpty()

/** 已装的最高 NDK；没装则退回这个（AGP 会联网下载） */
val ndkVersionToUse: String = installedNdkVersions
    .sortedWith(compareBy({ it.substringBefore('.').toIntOrNull() ?: 0 }, { it }))
    .lastOrNull()
    ?: "29.0.14206865"

android {
    namespace = "com.azime.input"
    compileSdk = 34

    defaultConfig {
        // 包名 Oime（原 com.azime.input；与旧版并存，需重新选择输入法）
        applicationId = "com.oime.input"
        // 轮19.95（**仅 dev/v7a 分支**）：minSdk 回到 **21** —— 目的就是在 Android 5.1(API 22)
        // 老设备上验证「给 so 补 DT_HASH」是否真能跑起来；main 分支仍保持 23 ✓
        // 轮19.89：**minSdk 21 → 23**（Android 6.0+）。
        // 原因：随包分发的预编译 so（libonnxruntime / librime / sherpa）只带 DT_GNU_HASH，
        // 而 Android 5.x 的 linker 强制要求 DT_HASH ⇒ 5.x 上 dlopen 必失败（能装但起不来 ✗）
        // ⇒ 与其"能装不能用"，不如把下限诚实地定在 6.0（API 23）✓
        minSdk = 23
        targetSdk = 34
        // 轮19.145：143 → 144（本机出的 debug 测试包，便于装机核对 ✓）
        // 测试通过后再统一升到正式版 1.0.4 ✓
        versionCode = 146
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // ABI 列表在下方 splits.abi 里按 jniLibs 实际内容配置（轮19.86 改为**按 ABI 拆分出包**）
    }

    // 轮19.91：**正式发布签名**（密钥不进仓库 ⇒ 由 CI Secrets 注入环境变量 ✓；
    // 本地或 fork 没设这些变量时自动回退 debug 签名，仍能构建 ✓）
    signingConfigs {
        create("oimeRelease") {
            val b64 = System.getenv("SIGNING_KEY")
            if (!b64.isNullOrBlank()) {
                val ks = File(rootProject.layout.buildDirectory.asFile.get(), "oime-release.jks")
                ks.parentFile?.mkdirs()
                ks.writeBytes(Base64.getDecoder().decode(b64))
                storeFile = ks
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    // 轮19.105：**恢复发布 armeabi-v7a**（K20P 实测通过后回归 ✓，仍按 ABI **分开出包** ✓）
    // ABI 列表按 jniLibs 实际内容自适应 ⇒ 放回 so 即自动纳入 ✓
    // 轮19.124：手写推理 JNI（薄壳 ✓ 链接 AAR 里的 onnxruntime ✓）
    // 轮19.147：NDK 版本**不再写死** —— 原因见文件末尾 `ndkVersionToUse` 的注释
    // （CI ubuntu-latest 与开发机的 NDK 集合不同，写死任一个都会让另一边去联网下 ~1GB ✗）
    ndkVersion = ndkVersionToUse

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                // 静态链接 libc++ ✓ ⇒ 不额外依赖 libc++_shared.so ✓
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            val abis = mutableListOf("arm64-v8a")
            if (file("src/main/jniLibs/armeabi-v7a/librime_jni.so").exists()) {
                abis += "armeabi-v7a"
            }
            include(*abis.toTypedArray())
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 轮19.91：有 SIGNING_KEY 时用**正式发布密钥** ✓；否则回退 debug（本地开发/fork ✓）
            signingConfig = if (!System.getenv("SIGNING_KEY").isNullOrBlank()) {
                signingConfigs.getByName("oimeRelease")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 轮19.147：**给 cpp/CMakeLists.txt 准备链接用的 libonnxruntime.so**
//   · 手写推理 JNI（app/src/main/cpp/handwriting_jni.cpp）要链接 onnxruntime；
//     CMakeLists 从 `app/build/ort-link/<abi>/libonnxruntime.so` 取它 ✓
//   · 而 app/build/ 是**构建产物目录、不进仓库** ✗ ⇒ 全新克隆（CI / fork）里这个文件不存在
//     ⇒ CMake 配置阶段直接 `FATAL_ERROR: 缺少链接用 libonnxruntime.so` ✗
//     （本机之所以一直能出包，是因为本机 app/build/ort-link/ 早有存量文件 —— 属于"看不见的依赖"）
//   ⇒ 这里把 sherpa-onnx AAR 里的 `jni/<abi>/libonnxruntime.so` 解出来 ✓
//   注：**只用于链接** ✓；运行时用的是 AGP 从 AAR 自动打进 APK 的那一份 ⇒ 不重复占体积 ✓
// ─────────────────────────────────────────────────────────────────────────────
val ortLinkDir = layout.buildDirectory.dir("ort-link")

val extractOrtForLink = tasks.register("extractOrtForLink") {
    group = "build"
    description = "从 app/libs/sherpa-onnx-*.aar 解出 libonnxruntime.so（CMake 链接用）"
    val aarFiles = fileTree("libs") { include("sherpa-onnx-*.aar") }
    inputs.files(aarFiles).withPropertyName("sherpaAar")
    outputs.dir(ortLinkDir).withPropertyName("ortLinkDir")
    doLast {
        val aar = aarFiles.files.maxByOrNull { it.lastModified() }
            ?: error("缺少 app/libs/sherpa-onnx-*.aar —— CI 会在构建前从官方 release 下载它")
        val root = ortLinkDir.get().asFile
        var n = 0
        ZipFile(aar).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("jni/") && it.name.endsWith("/libonnxruntime.so") }
                .forEach { entry ->
                    val abi = entry.name.substringAfter("jni/").substringBefore('/')
                    if (abi != "arm64-v8a" && abi != "armeabi-v7a") return@forEach
                    val dst = File(root, "$abi/libonnxruntime.so")
                    dst.parentFile?.mkdirs()
                    if (dst.exists() && dst.length() == entry.size) return@forEach
                    zip.getInputStream(entry).use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                    logger.lifecycle("  ort-link: $abi/libonnxruntime.so (${entry.size / 1024 / 1024} MB)")
                    n++
                }
        }
        logger.lifecycle("extractOrtForLink: 新解出 $n 个（已存在的跳过）")
    }
}

// CMake 的配置任务必须先跑 —— 否则 configure 阶段就因缺 so 报 FATAL_ERROR
// AGP 的原生任务名：configureCMake<BuildType>[<abi>] / buildCMake<BuildType>[<abi>]
tasks.configureEach {
    val taskName = name
    if (taskName.startsWith("configureCMake") || taskName.startsWith("buildCMake")) {
        dependsOn(extractOrtForLink)
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Lua (LuaJ)
    
    // Zip4j for zip handling
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // sherpa-onnx 本地语音识别（轮19：SenseVoice 离线 / zipformer 流式）
    // 官方 release AAR（含 Kotlin API + 全 ABI so），jitpack 拉取不稳定故提交进 repo
    implementation(files("libs/sherpa-onnx-1.13.5.aar"))
    // 轮19.127：解压 .tar.bz2 用（语音模型是 tar.bz2 ✗ Android 原生解不了 ✓）
    implementation("org.apache.commons:commons-compress:1.26.2")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
