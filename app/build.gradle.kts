plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val configuredClanApiBaseUrl = providers.gradleProperty("CLAN_API_BASE_URL")
    .map { if (it.endsWith('/')) it else "$it/" }
val debugClanApiBaseUrl = configuredClanApiBaseUrl.orElse("http://10.0.2.2:8080/")
val releaseClanApiBaseUrl = configuredClanApiBaseUrl.orElse("https://clan.invalid/")

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
<<<<<<< HEAD
=======
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
    implementation(libs.kotlinx.serialization.json)
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
