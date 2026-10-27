plugins {
    id("io.micronaut.build.internal.jms-module")
}

dependencies {
    annotationProcessor(mnValidation.micronaut.validation.processor)
    implementation(mnValidation.micronaut.validation)
    api(mn.micronaut.messaging)
    api(libs.managed.jakarta.jms.api)
    api(libs.commons.pool2)
    implementation(mn.micronaut.jackson.databind)
    compileOnly(mn.micronaut.management)
    compileOnly(mnMicrometer.micrometer.core)
    compileOnly(platform(mnTracing.boms.opentelemetry))
    compileOnly(libs.opentelemetry.api)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mn.micronaut.management)
    testImplementation(mnMicrometer.micrometer.core)
    testImplementation(platform(mnTracing.boms.opentelemetry))
    testImplementation(libs.opentelemetry.sdk.testing)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
