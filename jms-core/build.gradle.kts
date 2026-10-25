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

    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
