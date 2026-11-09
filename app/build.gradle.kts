plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.davicolzer.zureader"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.davicolzer.zureader"
        minSdk = 26
        targetSdk = 37
        // Número da build = quantidade de commits (o CI informa em ZU_BUILD_NUMBER). Sempre cresce,
        // em qualquer fluxo, para o Android aceitar a atualização por cima da versão anterior.
        versionCode = providers.environmentVariable("ZU_BUILD_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = "1.0.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Assinatura do release: só quando as variáveis existem (CI com secrets ou máquina local).
    // Sem elas, o release sai sem assinatura. Ver docs/RELEASE.md.
    val keystorePath = providers.environmentVariable("ZU_KEYSTORE_PATH").orNull
    signingConfigs {
        // Chave de debug fixa (senha pública "android"), só para testes: assim cada APK de
        // teste gerado pelo CI instala por cima do anterior. Não usar para publicar.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("ZU_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ZU_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ZU_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        // versão de teste (APK dos PRs e do Android Studio): outro app, que instala ao lado do oficial
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-teste"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // versão para testar no celular antes de ir para a main (branch staging): igual à oficial
        // (otimizada, assinada com a chave de publicação), mas outro app, que instala ao lado dela
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            // sem a chave de publicação (no computador), assina com a de teste para poder instalar
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // o idioma é escolhido dentro do app: o AAB precisa levar todas as traduções
    bundle {
        language {
            enableSplit = false
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:vision"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    // vídeo maior que 720p é convertido para 720p antes de guardar
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}

// testes de unidade do app (sem aparelho) com JUnit 6, como nos módulos core
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
