plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val configuredClanApiBaseUrl = providers.gradleProperty("CLAN_API_BASE_URL")
    .map { if (it.endsWith('/')) it else "$it/" }
val debugClanApiBaseUrl = configuredClanApiBaseUrl.orElse("http://192.168.110.3:8080/")
val releaseClanApiBaseUrl = configuredClanApiBaseUrl.orElse("https://clan.invalid/")

// WebRTC ICE 配置：默认 STUN 只负责 NAT 反射地址发现，媒体仍走 P2P；
// 对称 NAT/跨运营商场景需要通过 -PCLAN_TURN_URL 等属性配置独立 TURN。
val configuredStunUrls = providers.gradleProperty("CLAN_STUN_URLS")
    .orElse("stun:stun.l.google.com:19302")
val configuredTurnUrl = providers.gradleProperty("CLAN_TURN_URL").orElse("")
val configuredTurnUsername = providers.gradleProperty("CLAN_TURN_USERNAME").orElse("")
val configuredTurnPassword = providers.gradleProperty("CLAN_TURN_PASSWORD").orElse("")
val buildConfigString: (String) -> String = { value ->
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.fantaysea.clan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.fantaysea.clan"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLAN_STUN_URLS", buildConfigString(configuredStunUrls.get()))
        buildConfigField("String", "CLAN_TURN_URL", buildConfigString(configuredTurnUrl.get()))
        buildConfigField("String", "CLAN_TURN_USERNAME", buildConfigString(configuredTurnUsername.get()))
        buildConfigField("String", "CLAN_TURN_PASSWORD", buildConfigString(configuredTurnPassword.get()))
    }

    buildTypes {
        debug {
            buildConfigField("String", "CLAN_API_BASE_URL", "\"${debugClanApiBaseUrl.get()}\"")
        }
        release {
            buildConfigField("String", "CLAN_API_BASE_URL", "\"${releaseClanApiBaseUrl.get()}\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.webrtc.android)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
