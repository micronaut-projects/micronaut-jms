package io.micronaut.jms.docs.concurrency

import io.micronaut.jms.docs.AbstractJmsSpec
import spock.util.concurrent.PollingConditions

class ConcurrencySpec extends AbstractJmsSpec {

    void 'jobs are handled by three consumers'() {
        given:
        PollingConditions polling = new PollingConditions(timeout: 20)

        when:
        JobProducer producer = applicationContext.getBean(JobProducer)
        30.times { producer.submit("job-${it}".toString()) }
        JobListener listener = applicationContext.getBean(JobListener)

        then:
        polling.eventually {
            listener.processed.get() == 30
            listener.sessions.size() > 1
        }
    }
}
