package no.nav.helse.flex.service

import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.domene.tilAnnulleringDto
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class MottakAnnulering(
    private val annulleringDAO: AnnulleringDAO,
    private val metrikk: Metrikk,
) {
    private val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        if (cr.erAnnullering()) {
            mottaAnnullering(
                id = UUID.nameUUIDFromBytes("${cr.partition()}-${cr.offset()}".toByteArray()),
                fnr = cr.key(),
                annullering = cr.value(),
                opprettet = Instant.now()
            )
        }
    }

    fun mottaAnnullering(id: UUID, fnr: String, annullering: String, opprettet: Instant) {
        val annulleringSerialisert = try {
            annullering.tilAnnulleringDto()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere annulering", e)
        }

        annulleringDAO.finnAnnullering(fnr)
            .firstOrNull { it.annullering == annulleringSerialisert }
            ?.let {
                if (it.id == id.toString()) {
                    log.info("Annullering $id er allerede mottat, går videre")
                } else {
                    log.warn("Oppretter ikke duplikate annulleringer ny id: $id, eksisterende id: ${it.id}")
                }
                return
            }

        annulleringDAO.opprettAnnullering(
            id = id,
            fnr = fnr,
            annullering = annullering,
            opprettet = opprettet
        )

        metrikk.MOTTATT_ANNULLERING_VEDTAK.increment()

        log.info("Opprettet annullering med spinnsyn databaseid $id")
    }
}

private fun ConsumerRecord<String, String>.erAnnullering(): Boolean {
    return headers().any { header ->
        header.key() == "type" && String(header.value()) == "Annullering"
    }
}
