import java.util.Properties
import java.io.FileInputStream
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.application.zaona.weather"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.application.zaona.weather"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "2.1.1"

        val weatherLocalProps = Properties().apply {
            val weatherLocalPropsFile = rootProject.file("weather.local.properties")
            if (!weatherLocalPropsFile.exists()) {
                throw GradleException("Missing weather.local.properties. Please create it and set weatherBackendBaseUrl/weatherClientType/weatherApiKey.")
            }
            load(FileInputStream(weatherLocalPropsFile))
        }
        val backendBaseUrl = weatherLocalProps.getProperty("weatherBackendBaseUrl", "").trim()
            .ifEmpty { throw GradleException("weatherBackendBaseUrl is empty in weather.local.properties.") }
        val weatherClientType = weatherLocalProps.getProperty("weatherClientType", "").trim()
            .ifEmpty { throw GradleException("weatherClientType is empty in weather.local.properties.") }
        val weatherApiKey = weatherLocalProps.getProperty("weatherApiKey", "").trim()
            .ifEmpty { throw GradleException("weatherApiKey is empty in weather.local.properties.") }
        buildConfigField("String", "WEATHER_BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "WEATHER_CLIENT_TYPE", "\"$weatherClientType\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"$weatherApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("key.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))

                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val name = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
            val baseAbiVersionCode = mapOf(
                "armeabi-v7a" to 1,
                "arm64-v8a" to 2,
                "x86_64" to 3
            )[name]
            if (baseAbiVersionCode != null) {
                output.versionCode.set(baseAbiVersionCode * 1000 + (output.versionCode.get() ?: 0))
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("com.microsoft.clarity:clarity-compose:3.+")
    implementation(libs.miuix.ui.android)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.preference.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
