package no.nav.helse.flex.service

import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.db.VedtakDAO
import no.nav.helse.flex.domene.tilAnnulleringDto
import no.nav.helse.flex.domene.tilVedtakDto
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class RetroMottaVedtakService(
    private val vedtakDAO: VedtakDAO,
    private val annulleringDAO: AnnulleringDAO,
    private val metrikk: Metrikk,
) {
    private val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        if (cr.erVedtak()) {
            mottaVedtak(
                id = UUID.nameUUIDFromBytes("${cr.partition()}-${cr.offset()}".toByteArray()),
                fnr = cr.key(),
                vedtak = cr.value(),
                opprettet = Instant.now()
            )
        } else if (cr.erAnnullering()) {
            mottaAnnullering(
                id = UUID.nameUUIDFromBytes("${cr.partition()}-${cr.offset()}".toByteArray()),
                fnr = cr.key(),
                annullering = cr.value(),
                opprettet = Instant.now()
            )
        }
    }

    fun mottaVedtak(id: UUID, fnr: String, vedtak: String, opprettet: Instant) {

        val vedtakSerialisert = try {
            vedtak.tilVedtakDto()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere vedtak", e)
        }

        vedtakDAO.finnVedtak(fnr)
            .firstOrNull { it.vedtak == vedtakSerialisert }
            ?.let {
                if (it.id == id.toString()) {
                    log.info("Vedtak $id er allerede mottatt, går videre")
                } else {
                    log.warn("Oppretter ikke duplikate vedtak ny id: $id, eksisterende id: ${it.id}")
                }
                return
            }

        vedtakDAO.opprettVedtak(fnr = fnr, vedtak = vedtak, id = id, opprettet = opprettet)

        log.info("Opprettet vedtak med spinnsyn databaseid $id")

        metrikk.MOTTATT_VEDTAK.increment()

        if (vedtakSerialisert.automatiskBehandling) {
            metrikk.MOTTATT_AUTOMATISK_VEDTAK.increment()
        } else {
            metrikk.MOTTATT_MANUELT_VEDTAK.increment()
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

private fun ConsumerRecord<String, String>.erVedtak(): Boolean {
    return headers().any { header ->
        header.key() == "type" && String(header.value()) == "Vedtak"
    }
}

private fun ConsumerRecord<String, String>.erAnnullering(): Boolean {
    return headers().any { header ->
        header.key() == "type" && String(header.value()) == "Annullering"
    }
}
