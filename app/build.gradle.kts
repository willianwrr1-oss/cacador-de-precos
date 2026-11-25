import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildDate: String = SimpleDateFormat("dd/MM/yyyy").apply {
    timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
}.format(Date())

android {
    namespace = "com.willian.cacadorprecos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.willian.cacadorprecos"
        minSdk = 29
        targetSdk = 34
        // sobe a cada build do GitHub Actions
        versionCode = maxOf(1, (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt())
        versionName = "1.3.0"
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    signingConfigs {
        create("fixa") {
            storeFile = file("precos.jks")
            storePassword = "precos2026ssa"
            keyAlias = "precos"
            keyPassword = "precos2026ssa"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixa")
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
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
}
