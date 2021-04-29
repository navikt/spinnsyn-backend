package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import org.springframework.kafka.event.ConsumerStoppedEvent
import org.springframework.kafka.listener.KafkaMessageListenerContainer

object ConsumerStoppedEventExt {
    val log = logger()

    fun ConsumerStoppedEvent.restart() {
        if (this.reason == ConsumerStoppedEvent.Reason.NORMAL) {
            log.debug("Consumer stoppet grunnet NORMAL")
            return
        }
        log.error("Consumer stoppet grunnet ${this.reason}")
        if (this.source is KafkaMessageListenerContainer<*, *> &&
            this.reason == ConsumerStoppedEvent.Reason.AUTH
        ) {
            val container = this.source as KafkaMessageListenerContainer<*, *>
            log.info("Restarter consumer, creds kan ha blitt rotert")
            container.start()
        }
    }
}
