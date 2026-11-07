import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

// O Gradle roda no JDK 25 (LTS). O bytecode sai em Java 21 para ser compatível com o Android.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    api(project(":core:zu"))
    // QR codes (Java puro, sem internet)
    implementation(libs.zxing.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.zxing.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // fotos reais do jogo (testdata/real) usadas como teste de regressão
    systemProperty("zu.testdata", rootProject.file("testdata/real").absolutePath)
    inputs.dir(rootProject.file("testdata/real")).withPropertyName("fotosReais").optional()
    // gerador de dados da rede de glifos (só roda com ZU_GEN definido)
    providers.environmentVariable("ZU_GEN").orNull?.let { environment("ZU_GEN", it) }
}
