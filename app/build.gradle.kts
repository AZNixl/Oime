plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.azime.input"
    compileSdk = 34

    defaultConfig {
        // 包名 Oime（原 com.azime.input；与旧版并存，需重新选择输入法）
        applicationId = "com.oime.input"
        // 轮19.89：**minSdk 21 → 23**（Android 6.0+）。
        // 原因：随包分发的预编译 so（libonnxruntime / librime / sherpa）只带 DT_GNU_HASH，
        // 而 Android 5.x 的 linker 强制要求 DT_HASH ⇒ 5.x 上 dlopen 必失败（能装但起不来 ✗）
        // ⇒ 与其"能装不能用"，不如把下限诚实地定在 6.0（API 23）✓
        minSdk = 23
        targetSdk = 34
        versionCode = 107
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // ABI 列表在下方 splits.abi 里按 jniLibs 实际内容配置（轮19.86 改为**按 ABI 拆分出包**）
    }

    // 轮19.90：**只发布 arm64-v8a**（用户决定：v7a 撤回，老设备场景不划算 ✓）
    // ABI 列表仍按 jniLibs 实际内容自适应 ⇒ 将来把 armeabi-v7a 的 so 放回即自动恢复 ✓
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
            // 轮19.84：发行包用 **debug keystore** 签名 —— 好处是**不需要任何 secrets**（fork 也能直接出包 ✓），
            // 缺点是签名与"正式密钥"不同（将来换正式密钥需卸载重装）。个人开源项目这样做最常见 ✓
            signingConfig = signingConfigs.getByName("debug")
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
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
