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
        minSdk = 24
        targetSdk = 34
        versionCode = 33
        versionName = "0.9.23-oime"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            // librime_jni.so 目前仅提供 arm64-v8a（提取自 Xime 2.6.2）
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation("org.luaj:luaj-jse:3.0.1")
    
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
