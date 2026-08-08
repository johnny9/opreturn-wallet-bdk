plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.opreturnwallet.bdk"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.opreturnwallet.bdk"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "ENABLE_DEBUG_CONSUME_UTXO", "false")
        buildConfigField("boolean", "MAINNET_TRIAL", "false")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_DEBUG_CONSUME_UTXO", "true")
        }
        create("mainnetTrial") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".mainnettrial"
            versionNameSuffix = "-mainnet-trial"
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "ENABLE_DEBUG_CONSUME_UTXO", "false")
            buildConfigField("boolean", "MAINNET_TRIAL", "true")
            resValue("string", "app_name", "OP_RETURN Wallet Mainnet Trial")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    //noinspection NewerVersionAvailable -- 3.x is a deliberate future migration; variant/2.0 uses the 2.x API.
    implementation("org.bitcoindevkit:bdk-android:2.3.1") {
        // bdk-android does not reference this optional API; omit it so the wallet ships no
        // application logging facade that could later be bound to Logcat accidentally.
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
