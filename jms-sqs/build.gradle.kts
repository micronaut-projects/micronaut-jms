plugins {
    id("io.micronaut.build.internal.jms-module")
}

dependencies {
    annotationProcessor(mn.micronaut.graal)
    annotationProcessor(mnValidation.micronaut.validation.processor)
    implementation(mnValidation.micronaut.validation)
    api(projects.micronautJmsCore)
    api(libs.amazon.sqs.messaging)
    api(libs.aws.sqs)
    api(mnAws.micronaut.aws.sdk.v2)
    compileOnly(libs.graal.svm)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
