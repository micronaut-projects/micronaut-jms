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
    testImplementation(platform(mnTest.micronaut.test.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
