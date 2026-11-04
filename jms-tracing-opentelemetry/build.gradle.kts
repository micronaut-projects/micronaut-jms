plugins {
    id("io.micronaut.build.internal.jms-module")
}

dependencies {
    api(projects.micronautJmsCore)
    api(mnTracing.micronaut.tracing.opentelemetry)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(platform(mnTracing.boms.opentelemetry))
    testImplementation(libs.opentelemetry.sdk.testing)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    // the first release of the module
    binaryCompatibility.enabledAfter("5.2.0")
}

tasks.test {
    useJUnitPlatform()
}
