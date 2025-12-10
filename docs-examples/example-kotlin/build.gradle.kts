plugins {
    id("io.micronaut.build.internal.kotlin-kapt")
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
