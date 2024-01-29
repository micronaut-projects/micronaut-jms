plugins {
    id("io.micronaut.build.internal.jms-module")
}

dependencies {
    annotationProcessor(mnValidation.micronaut.validation.processor)
    implementation(mnValidation.micronaut.validation)
    api(projects.micronautJmsCore)
    api(libs.managed.activemq.client.jakarta)
    implementation(libs.spotbugs)
}
