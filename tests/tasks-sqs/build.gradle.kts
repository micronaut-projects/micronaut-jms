plugins {
    id("io.micronaut.build.internal.jms-tests")
    id("io.micronaut.build.internal.jms-native-tests")
}

dependencies {
    implementation(projects.micronautJmsSqs)
    testImplementation(libs.awaitility)
    testImplementation(platform(mnTest.boms.testcontainers))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.localstack)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}
