package no.nav.helse.flex.vedtak.service

import RSOppdrag
import RSUtbetalingUtbetalt
import RSUtbetalingslinje
import RSVedtak
import RSVedtakWrapper
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import no.nav.helse.flex.vedtak.api.AbstractApiError
import no.nav.helse.flex.vedtak.api.LogLevel
import no.nav.helse.flex.vedtak.db.Annullering
import no.nav.helse.flex.vedtak.db.AnnulleringDAO
import no.nav.helse.flex.vedtak.db.Vedtak
import no.nav.helse.flex.vedtak.db.VedtakDAO
import no.nav.helse.flex.vedtak.domene.Periode
import no.nav.helse.flex.vedtak.domene.VedtakDto
import no.nav.helse.flex.vedtak.domene.tilAnnulleringDto
import no.nav.helse.flex.vedtak.domene.tilVedtakDto
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

@Service
class RetroVedtakService(
    private val vedtakDAO: VedtakDAO,
    private val annulleringDAO: AnnulleringDAO,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
    private val metrikk: Metrikk,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
    @Value("\${spinnsyn-frontend.url}") private val spinnsynFrontendUrl: String,
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

    fun hentVedtak(fnr: String): List<RSVedtakWrapper> {
        return hentRetroVedtak(fnr)
            .filter {
                if (it.vedtak.organisasjonsnummer == null) {
                    log.warn("Forventet at vedtak ${it.id} har orgnummer")
                    false
                } else {
                    true
                }
            }
            .filter { rsVedtak ->
                if (rsVedtak.vedtak.utbetalinger.find { it.fagområde == "SPREF" } == null) {
                    log.warn("Forventet at vedtak ${rsVedtak.id} har SPREF utbetaling")
                    false
                } else {
                    true
                }
            }
            .map { it.tilRSVedtakWrapper() }
    }

    fun hentRetroVedtak(fnr: String): List<RetroRSVedtak> {
        val annulleringer = annulleringDAO.finnAnnullering(fnr)
        return vedtakDAO.finnVedtak(fnr)
            .map { it.tilRetroRSVedtak(annulleringer.forVedtak(it)) }
    }

    fun lesVedtak(fnr: String, vedtaksId: String): String {
        if (hentRetroVedtak(fnr).none { it.id == vedtaksId }) {
            throw VedtakIkkeFunnetException()
        }

        val bleLest = vedtakDAO.lesVedtak(fnr, vedtaksId)
        if (bleLest) {
            brukernotifikasjonKafkaProdusent.sendDonemelding(
                Nokkel(serviceuserUsername, vedtaksId),
                Done(Instant.now().toEpochMilli(), fnr, vedtaksId)
            )
            metrikk.VEDTAK_LEST.increment()
            return "Leste vedtak $vedtaksId"
        }
        return "Vedtak $vedtaksId er allerede lest"
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
                    log.info("Vedtak $id er allerede mottat, går videre")
                } else {
                    log.warn("Oppretter ikke duplikate vedtak ny id: $id, eksisterende id: ${it.id}")
                }
                return
            }

        val vedtaket = vedtakDAO.opprettVedtak(fnr = fnr, vedtak = vedtak, id = id, opprettet = opprettet)

        log.info("Opprettet vedtak med spinnsyn databaseid $id")

        brukernotifikasjonKafkaProdusent.opprettBrukernotifikasjonOppgave(
            Nokkel(serviceuserUsername, id.toString()),
            Oppgave(
                vedtaket.opprettet.toEpochMilli(),
                fnr,
                id.toString(),
                "Sykepengene dine er beregnet - se resultatet",
                "$spinnsynFrontendUrl/vedtak/$id",
                4,
                true
            )
        )

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

data class RetroRSVedtak(
    val id: String,
    val lest: Boolean,
    val lestDato: OffsetDateTime? = null,
    val vedtak: VedtakDto,
    val opprettet: LocalDate,
    val annullert: Boolean = false
)

fun Vedtak.tilRetroRSVedtak(annullering: Boolean = false): RetroRSVedtak {
    return RetroRSVedtak(
        id = this.id,
        lest = this.lest,
        lestDato = this.lestDato,
        vedtak = this.vedtak,
        opprettet = LocalDate.ofInstant(this.opprettet, ZoneId.of("Europe/Oslo")),
        annullert = annullering
    )
}

fun RetroRSVedtak.tilRSVedtakWrapper(): RSVedtakWrapper {
    val arbeidsgiveroppdrag = this.vedtak.utbetalinger.first { it.fagområde == "SPREF" }
    return RSVedtakWrapper(
        id = this.id,
        annullert = this.annullert,
        lest = this.lest,
        lestDato = this.lestDato,
        opprettet = this.opprettet,
        vedtak = RSVedtak(
            organisasjonsnummer = this.vedtak.organisasjonsnummer!!,
            dokumenter = this.vedtak.dokumenter,
            sykepengegrunnlag = this.vedtak.sykepengegrunnlag,
            inntekt = this.vedtak.månedsinntekt,
            fom = this.vedtak.fom,
            tom = this.vedtak.tom,
            utbetaling = RSUtbetalingUtbetalt(
                organisasjonsnummer = this.vedtak.organisasjonsnummer,
                forbrukteSykedager = this.vedtak.forbrukteSykedager,
                gjenståendeSykedager = this.vedtak.gjenståendeSykedager,
                automatiskBehandling = this.vedtak.automatiskBehandling,
                utbetalingsdager = emptyList(),
                arbeidsgiverOppdrag = RSOppdrag(
                    mottaker = arbeidsgiveroppdrag.mottaker,
                    nettoBeløp = arbeidsgiveroppdrag.totalbeløp,
                    utbetalingslinjer = arbeidsgiveroppdrag.utbetalingslinjer.map { it.tilRsUtbetalingslinje() }
                )
            )
        )
    )
}

private fun VedtakDto.UtbetalingDto.UtbetalingslinjeDto.tilRsUtbetalingslinje(): RSUtbetalingslinje {
    return RSUtbetalingslinje(
        dagsats = this.dagsats,
        fom = this.fom,
        tom = this.tom,
        grad = this.grad,
        totalbeløp = this.beløp
    )
}

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

class VedtakIkkeFunnetException : AbstractApiError(
    message = "Fant ikke vedtak",
    httpStatus = HttpStatus.NOT_FOUND,
    reason = "VEDTAK_IKKE_FUNNET",
    loglevel = LogLevel.WARN
)
