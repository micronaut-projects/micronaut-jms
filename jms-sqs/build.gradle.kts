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
    api(libs.micronaut.aws.v2)
    compileOnly(libs.graal.svm)
}
