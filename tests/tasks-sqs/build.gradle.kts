plugins {
    id("io.micronaut.build.internal.jms-tests")
    id("io.micronaut.build.internal.jms-native-tests")
}

dependencies {
    implementation(projects.micronautJmsSqs)
    testImplementation(libs.awaitility)
}

micronaut {
    testResources {
        additionalModules.add("localstack-sqs")
    }
}
