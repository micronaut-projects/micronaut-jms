plugins {
    id("io.micronaut.build.internal.jms-tests")
    id("io.micronaut.build.internal.jms-native-tests")
}

dependencies {
    implementation(projects.micronautJmsActivemqArtemis)
    testImplementation(libs.awaitility)
    testImplementation(platform(mnTest.boms.testcontainers))
    testImplementation(libs.testcontainers.activemq)
    testImplementation(libs.testcontainers.junit.jupiter)
}
