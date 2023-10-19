plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
    id("io.micronaut.build.internal.jms-examples")
}
dependencies {
    testImplementation(libs.awaitility)
}
micronaut {
    importMicronautPlatform.set(false)
    testRuntime("kotest5")
}
application {
    mainClass.set("com.example.ApplicationKt")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
