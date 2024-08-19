plugins {
    id("io.micronaut.build.internal.jms-tests")
    id("io.micronaut.build.internal.jms-native-tests")
}

dependencies {
    implementation(projects.micronautJmsActivemqArtemis)
    testImplementation(libs.awaitility)
    testImplementation(platform(mnTestResources.boms.testcontainers))
    testImplementation(libs.testcontainers.junit.jupiter)
}
