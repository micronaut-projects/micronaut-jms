package io.micronaut.jms.docs

import io.kotest.core.spec.style.BehaviorSpec
import io.micronaut.context.ApplicationContext
import java.util.UUID

abstract class AbstractJmsKotest(body: BehaviorSpec.() -> Unit = {}): BehaviorSpec(body) {

    companion object {

        fun startContext(specName: String) =
            startContext(getDefaultConfig(specName))

        fun startContext(configuration: Map<String, Any>) =
            ApplicationContext.run(configuration, "test")

        fun getDefaultConfig(specName: String): MutableMap<String, Any> {
            val broker = UUID.randomUUID().toString().replace("-".toRegex(), "")
            return mutableMapOf(
                "micronaut.jms.activemq.classic.connectionString" to "vm://$broker?broker.persistent=false",
                "micronaut.jms.activemq.artemis.enabled" to false,
                "micronaut.jms.activemq.classic.enabled" to true,
                "micronaut.jms.activemq.sqs.enabled" to false,
                "spec.name" to specName)

        }
    }
}
