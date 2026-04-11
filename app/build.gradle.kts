plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zendeck.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zendeck.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Read signing config from env vars (injected by GitHub Actions)
    val storeFile = System.getenv("SIGNING_STORE_FILE")
    val storePassword = System.getenv("SIGNING_STORE_PASSWORD")
    val keyAlias = System.getenv("SIGNING_KEY_ALIAS")
    val keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
    val hasSigningConfig = storeFile != null && storePassword != null &&
            keyAlias != null && keyPassword != null

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                this.storeFile = file(storeFile!!)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        // litertlm-android:0.10.0 was compiled with Kotlin 2.3.0 metadata.
        // Our project uses Kotlin 2.0.21 which only reads up to 2.0.0 metadata.
        // This flag tells the compiler to skip the version check so KSP/Room can proceed.
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

// Kotlin 2.0 merged kotlin-stdlib-jdk7/jdk8 into kotlin-stdlib.
// Some transitive deps still pull in the pre-2.0 artifacts, causing clashes.
// Substitute both jdk7/jdk8 with kotlin-stdlib itself so they never land
// on the compile classpath.
configurations.all {
    resolutionStrategy {
        // Force Kotlin runtime libs to the project's compiler version so that
        // litertlm-android's transitive kotlin-reflect:2.3.x / kotlin-stdlib:2.3.x
        // don't land on the runtime classpath compiled against a different stdlib ABI.
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
        force("org.jetbrains.kotlin:kotlin-reflect:2.0.21")

        dependencySubstitution {
            substitute(module("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
                .using(module("org.jetbrains.kotlin:kotlin-stdlib:2.0.21"))
                .because("kotlin-stdlib-jdk8 merged into kotlin-stdlib in Kotlin 2.0")
            substitute(module("org.jetbrains.kotlin:kotlin-stdlib-jdk7"))
                .using(module("org.jetbrains.kotlin:kotlin-stdlib:2.0.21"))
                .because("kotlin-stdlib-jdk7 merged into kotlin-stdlib in Kotlin 2.0")
        }
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Jsoup
    implementation(libs.jsoup)

    // LiteRT-LM — Gemma 4 E2B / E4B inference (.litertlm files)
    implementation(libs.litertlm.android)

    // Chrome Custom Tabs
    implementation(libs.androidx.browser)

    // Glance (App Widget)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Coroutines
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // NanoHTTPD – embedded LAN server
    implementation(libs.nanohttpd)

    // Coil (image loading)
    implementation(libs.coil.compose)

    // MLKit Text Recognition (OCR for screenshots)
    implementation(libs.mlkit.text.recognition)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
