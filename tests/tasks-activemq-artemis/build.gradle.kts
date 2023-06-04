plugins {
    id("io.micronaut.build.internal.jms-tests")
    id("io.micronaut.build.internal.jms-native-tests")
}

dependencies {
    implementation(projects.micronautJmsActivemqArtemis)
    testImplementation(libs.awaitility)
}
micronaut {
    testRuntime("junit5")
}
