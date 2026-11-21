plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.meubanco.patrimonio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.meubanco.patrimonio"
        minSdk = 26
        targetSdk = 34
        buildConfigField("String", "BACKEND_URL", "\"" + (System.getenv("BACKEND_URL") ?: "") + "\"")
        buildConfigField("boolean", "PLUGGY_SANDBOX", (System.getenv("PLUGGY_SANDBOX") == "1").toString())
        versionCode = 58
        versionName = "58.0-kotlin"
    }

    // Chave fixa (vem dos Secrets do GitHub). Sem ela, cai na chave de debug e o app continua compilando.
    val temChave = System.getenv("KEYSTORE_FILE")?.let { File(it).exists() } == true
    signingConfigs {
        create("fixa") {
            if (temChave) {
                storeFile = File(System.getenv("KEYSTORE_FILE")!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { if (temChave) signingConfig = signingConfigs.getByName("fixa") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // sem chave própria ainda: assina com a chave de debug para poder instalar. Troque por uma chave sua antes de publicar na loja.
            signingConfig = if (temChave) signingConfigs.getByName("fixa") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true; buildConfig = true }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // leitor de código de barras
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
}
