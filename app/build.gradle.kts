import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

// Load local.properties for private server URLs (not committed to git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localBool(key: String, default: Boolean): Boolean {
    return localProps.getProperty(key)?.trim()?.lowercase()?.let {
        it == "true" || it == "1" || it == "yes" || it == "on"
    } ?: default
}

fun sanitizeServerUrl(raw: String?, fallback: String): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return fallback
    // Disallow raw IPv4 or IPv6 addresses from ever being baked into release/debug builds
    val ipRegex = Regex("""^https?://(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}|\[[0-9a-fA-F:]+\])""")
    if (ipRegex.containsMatchIn(trimmed)) {
        return fallback
    }
    return trimmed
}

// Play Store upload key lives in keystore.properties at the repo root. File
// is gitignored; a template lives at playstore/keystore.properties.example.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning: Boolean = keystoreProps.getProperty("storeFile")?.isNotBlank() == true

// CI builds encode GitHub's run number into versionCode so the app's build
// number matches the release tag (v1.0.<run_number>). Local dev builds get 1.
// Explicit env overrides (ANDROID_VERSION_CODE / ANDROID_VERSION_NAME) allow
// the manual Play Publish workflow to set a high monotonic version code.
val ciRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val appVersionCode: Int = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: ciRunNumber
val appVersionName: String = System.getenv("ANDROID_VERSION_NAME")
    ?: "1.0.$appVersionCode"

android {
    namespace = "com.charles.livecaptionn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.charles.livecaptionn"
        minSdk = 28
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Inject server URLs; sanitizes any raw IP address so builds never ship baked-in IPs
        val defaultTranslate = sanitizeServerUrl(localProps.getProperty("translate.url"), "http://localhost:3006")
        val defaultStt = sanitizeServerUrl(localProps.getProperty("stt.url"), "http://localhost:9000/asr?output=json")
        buildConfigField("String", "DEFAULT_TRANSLATE_URL", "\"$defaultTranslate\"")
        buildConfigField("String", "DEFAULT_STT_URL", "\"$defaultStt\"")

        // GitHub repo that the in-app update checker queries for new releases.
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"trungkiendang\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"LiveTranscribe-Android\"")

    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "GITHUB_SELF_UPDATE_ENABLED", "true")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "GITHUB_SELF_UPDATE_ENABLED", "false")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val storeFileName = keystoreProps.getProperty("storeFile")
                val resolved = rootProject.file(storeFileName)
                storeFile = if (resolved.exists()) resolved else file(storeFileName)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // On-device translation (Google Translate models cached offline).
    implementation("com.google.mlkit:translate:17.0.3")

    // Firebase BoM pins compatible versions of every Firebase SDK below.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")
    // firebase-messaging and firebase-config were removed — not used.

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.11.0")

    // Play In-App Review — lets happy users leave a Play Store rating
    // without leaving the app. No-ops gracefully when Play Store isn't
    // installed (e.g. sideloaded github-flavor builds).


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Espresso 3.7 supports the Android 17 input APIs used by the Pixel test device.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
