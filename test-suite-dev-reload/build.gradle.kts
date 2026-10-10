plugins {
    java
    id("io.micronaut.build.internal.jms-base")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// -PdevReloadAwsVersion=<version> runs the suite against a micronaut-aws with development support, such as a
// snapshot published to Maven local, with which the client micronaut-aws builds is retained across a restart.
// Remove once the build moves to a micronaut-aws release that carries it.
val devReloadAwsVersion = providers.gradleProperty("devReloadAwsVersion")
if (devReloadAwsVersion.isPresent) {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.micronaut.aws") {
                useVersion(devReloadAwsVersion.get())
                because("the dev reload suite runs against the development support of micronaut-aws")
            }
        }
    }
}

dependencies {
    testAnnotationProcessor(platform(mn.micronaut.core.bom))
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(projects.micronautJmsActivemqArtemis)
    testImplementation(projects.micronautJmsSqs)
    testImplementation(mn.micronaut.dev.tck)
    // the reload harness compiles the application under test with the processors on the test classpath
    testImplementation(mn.micronaut.inject.java)
    testImplementation(platform(mnTest.boms.testcontainers))
    testImplementation(libs.testcontainers.activemq)
    testImplementation(libs.testcontainers.localstack)
    testImplementation(mnTest.junit.jupiter.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
    testRuntimeOnly(mnLogging.logback.classic)
}
