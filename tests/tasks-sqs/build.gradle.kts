plugins {
    id("io.micronaut.build.internal.jms-tests")
    id("io.micronaut.build.internal.jms-native-tests")
}

dependencies {
    implementation(projects.micronautJmsSqs)
    testImplementation(libs.awaitility)
    testImplementation(platform(mnTestResources.boms.testcontainers))
    testImplementation(libs.testcontainers.junit.jupiter)
}

micronaut {
    testResources {
        additionalModules.add("localstack-sqs")
    }
}
