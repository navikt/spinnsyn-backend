package no.nav.helse.flex.vedtak.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtak.db.Vedtak
import no.nav.helse.flex.vedtak.db.VedtakDAO
import no.nav.helse.flex.vedtak.domene.VedtakDto
import no.nav.helse.flex.vedtak.domene.tilVedtakDto
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

@Service
class VedtakService(
    private val vedtakDAO: VedtakDAO
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
        }
        /* // TODO: Håndter annullering
        else if (cr.erAnnullering()) {
            mottaAnnullering(
                id = UUID.nameUUIDFromBytes("${cr.partition()}-${cr.offset()}".toByteArray()),
                fnr = cr.key(),
                annullering = cr.value(),
                opprettet = Instant.now()
            )
        }
         */
    }

    fun hentVedtak(fnr: String): List<RSVedtak> {

        return vedtakDAO.finnVedtak(fnr).map { it.tilRSVedtak() }

        // TODO fiks annulleriong
    /*     val annulleringer = database.finnAnnullering(fnr)
        return database.finnVedtak(fnr)
            .map { it.tilRSVedtak(annulleringer.forVedtak(it)) }
            */
    }

    fun lesVedtak(fnr: String, vedtaksId: String): Boolean {
        val bleLest = vedtakDAO.lesVedtak(fnr, vedtaksId)
        if (bleLest) {
            /*  // TODO: Fix brukernotifikasjonKafkaProdusent
            brukernotifikasjonKafkaProdusent.sendDonemelding(
                Nokkel(environment.serviceuserUsername, vedtaksId),
                Done(Instant.now().toEpochMilli(), fnr, vedtaksId)
            )
             */
        }
        return bleLest
    }

    fun mottaVedtak(id: UUID, fnr: String, vedtak: String, opprettet: Instant) {

        val vedtakSerialisert = try {
            vedtak.tilVedtakDto()
        } catch (e: Exception) {
            log.error("Kunne ikke deserialisere vedtak", e)
            return
        }

        vedtakDAO.finnVedtak(fnr)
            .firstOrNull { it.vedtak == vedtakSerialisert }
            ?.let {
                if (it.id == id.toString()) {
                    log.info("Vedtak $id er allerede mottat, går videre")
                } else {
                    log.warn("Oppretter ikke duplikate vedtak ny id: $id, eksisterende id: ${it.id}")
                }
                return
            }

        val vedtaket = vedtakDAO.opprettVedtak(fnr = fnr, vedtak = vedtak, id = id, opprettet = opprettet)

        log.info("Opprettet vedtak med spinnsyn databaseid $id")

        /* // TODO: fix brukernotifikasjonKafkaProdusent
        brukernotifikasjonKafkaProdusent.opprettBrukernotifikasjonOppgave(
            Nokkel(environment.serviceuserUsername, id.toString()),
            Oppgave(
                vedtaket.opprettet.toEpochMilli(),
                fnr,
                id.toString(),
                "Sykepengene dine er beregnet - se resultatet",
                "${environment.spinnsynFrontendUrl}/vedtak/$id",
                4
            )
        )

        MOTTATT_VEDTAK.inc()

        if (vedtakSerialisert.automatiskBehandling) {
            MOTTATT_AUTOMATISK_VEDTAK.inc()
        } else {
            MOTTATT_MANUELT_VEDTAK.inc()
        }
         */
    }
/*
    fun mottaAnnullering(id: UUID, fnr: String, annullering: String, opprettet: Instant) {
        val annulleringSerialisert = try {
            annullering.tilAnnulleringDto()
        } catch (e: Exception) {
            log.error("Kunne ikke deserialisere annullering", e)
            return
        }

        vedtakDAO.finnAnnullering(fnr)
            .firstOrNull { it.annullering == annulleringSerialisert }
            ?.let {
                if (it.id == id.toString()) {
                    log.info("Annullering $id er allerede mottat, går videre")
                } else {
                    log.warn("Oppretter ikke duplikate annulleringer ny id: $id, eksisterende id: ${it.id}")
                }
                return
            }

        database.opprettAnnullering(
            id = id,
            fnr = fnr,
            annullering = annullering,
            opprettet = opprettet
        )

        MOTTATT_ANNULLERING_VEDTAK.inc()

        log.info("Opprettet annullering med spinnsyn databaseid $id")
    }

    fun hentVedtak(fnr: String, vedtaksId: String): RSVedtak? {
        val annulleringer = database.finnAnnullering(fnr)
        val vedtak = database.finnVedtak(fnr)
            .find { it.id == vedtaksId }
        return vedtak?.tilRSVedtak(annulleringer.forVedtak(vedtak))
    }

    fun eierVedtak(fnr: String, vedtaksId: String) =
        database.eierVedtak(fnr, vedtaksId)

    fun List<Annullering>.forVedtak(vedtak: Vedtak): Boolean =
        this.any {
            vedtak.matcherAnnullering(it)
        }

    fun Vedtak.matcherAnnullering(annullering: Annullering): Boolean {
        val vedtaksperiode = Periode(this.vedtak.fom, this.vedtak.tom)
        val annulleringsperiode = Periode(
            annullering.annullering.fom ?: return false,
            annullering.annullering.tom
                ?: return false
        )
        return vedtaksperiode.overlapper(annulleringsperiode) &&
            (
                this.vedtak.organisasjonsnummer == annullering.annullering.orgnummer ||
                    this.vedtak.utbetalinger.any { it.mottaker == annullering.annullering.orgnummer }
                )
    }
*/
}

data class RSVedtak(
    val id: String,
    val lest: Boolean,
    val lestDato: OffsetDateTime? = null,
    val vedtak: VedtakDto,
    val opprettet: LocalDate,
    val annullert: Boolean = false
)

fun Vedtak.tilRSVedtak(annullering: Boolean = false): RSVedtak {
    return RSVedtak(
        id = this.id,
        lest = this.lest,
        lestDato = this.lestDato,
        vedtak = this.vedtak,
        opprettet = LocalDate.ofInstant(this.opprettet, ZoneId.of("Europe/Oslo")),
        annullert = annullering
    )
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
