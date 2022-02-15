package no.nav.helse.flex.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.ConsumerStoppedEventExt.restart
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.organisasjon.OrganisasjonOppdatering
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.event.ConsumerStoppedEvent
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SykepengesoknadListener(
    val organisasjonsOppdatering: OrganisasjonOppdatering
) {

    @KafkaListener(
        topics = [FLEX_SYKEPENGESOKNAD_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val soknad = cr.value().tilSykepengesoknadDTO()
        organisasjonsOppdatering.handterSoknad(soknad)
        acknowledgment.acknowledge()
    }

    fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)

    @EventListener
    fun eventHandler(event: ConsumerStoppedEvent) {
        event.restart()
    }
}

const val FLEX_SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"
