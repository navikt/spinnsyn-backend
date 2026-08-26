package no.nav.helse.flex.kafka

import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.organisasjon.OrganisasjonOppdatering
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue

@Component
class SykepengesoknadListener(
    val organisasjonsOppdatering: OrganisasjonOppdatering,
) {
    @KafkaListener(
        topics = [FLEX_SYKEPENGESOKNAD_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val soknad = cr.value().tilSykepengesoknadDTO()
        organisasjonsOppdatering.handterSoknad(soknad)
        acknowledgment.acknowledge()
    }

    fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
}

const val FLEX_SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"
