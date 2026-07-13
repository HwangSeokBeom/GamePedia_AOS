plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Base URLs are environment configuration, not secrets: both hosts are already
// compiled into the shipped iOS client (see docs/DECISIONS.md D-0005).
val apiBaseUrlOverride: String? = providers.gradleProperty("gamepedia.apiBaseUrl").orNull
val stagingBaseUrl = apiBaseUrlOverride ?: "https://staging-gamepedia-api.duckdns.org"
val productionBaseUrl = apiBaseUrlOverride ?: "https://gamepedia-api.duckdns.org"

android {
    namespace = "com.hwb.gamepedia.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$stagingBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$productionBaseUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    api(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    api(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
