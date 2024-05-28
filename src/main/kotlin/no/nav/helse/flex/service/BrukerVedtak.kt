package no.nav.helse.flex.service

import no.nav.helse.flex.api.AbstractApiError
import no.nav.helse.flex.api.LogLevel
import no.nav.helse.flex.db.*
import no.nav.helse.flex.domene.*
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.organisasjon.LeggTilOrganisasjonnavn
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

@Service
class BrukerVedtak(
    private val identService: IdentService,
    private val vedtakRepository: VedtakRepository,
    private val utbetalingRepository: UtbetalingRepository,
    private val annulleringDAO: AnnulleringDAO,
    private val leggTilOrganisasjonavn: LeggTilOrganisasjonnavn,
    private val vedtakStatusProducer: VedtakStatusKafkaProducer,
) {
    val log = logger()

    enum class LesResultat {
        IKKE_FUNNET,
        LEST,
        ALLEREDE_LEST,
    }

    class VedtakIkkeFunnetException(vedtaksId: String) : AbstractApiError(
        message = "Fant ikke vedtak $vedtaksId",
        httpStatus = HttpStatus.NOT_FOUND,
        reason = "VEDTAK_IKKE_FUNNET",
        loglevel = LogLevel.WARN,
    )

    fun hentVedtak(
        fnr: String,
        hentSomBruker: Boolean = true,
    ): List<RSVedtakWrapper> {
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr)
        return finnAlleVedtak(identer.alle(), hentSomBruker)
            .markerRevurderte()
            .leggTilOrgnavn()
            .leggTilArbeidsgivere()
    }

    fun lesVedtak(
        fnr: String,
        vedtaksId: String,
    ): String {
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr)
        val lesUtbetaling = lesUtbetaling(identer = identer.alle(), utbetalingsId = vedtaksId)

        if (lesUtbetaling == LesResultat.IKKE_FUNNET) {
            throw VedtakIkkeFunnetException(vedtaksId)
        }

        if (lesUtbetaling == LesResultat.ALLEREDE_LEST) {
            return "Vedtak $vedtaksId er allerede lest"
        }

        vedtakStatusProducer.produserMelding(
            VedtakStatusDTO(fnr = fnr, id = vedtaksId, vedtakStatus = VedtakStatus.LEST),
        )

        utbetalingRepository.updateLestByIdentAndId(
            lest = Instant.now(),
            identer = identer.alle(),
            id = vedtaksId,
        )

        return "Leste vedtak $vedtaksId"
    }

    private fun lesUtbetaling(
        identer: List<String>,
        utbetalingsId: String,
    ): LesResultat {
        val utbetalingDbRecord =
            utbetalingRepository
                .findUtbetalingDbRecordsByIdent(identer)
                .find { it.id == utbetalingsId }
                ?: return LesResultat.IKKE_FUNNET

        if (utbetalingDbRecord.lest != null) {
            return LesResultat.ALLEREDE_LEST
        }

        return LesResultat.LEST
    }

    private fun List<RSVedtakWrapper>.leggTilOrgnavn(): List<RSVedtakWrapper> {
        return leggTilOrganisasjonavn.leggTilOrganisasjonnavn(this)
    }

    private fun List<RSVedtakWrapper>.leggTilArbeidsgivere(): List<RSVedtakWrapper> {
        return leggTilOrganisasjonavn.leggTilAndreArbeidsgivere(this)
    }

    private fun finnAlleVedtak(
        identer: List<String>,
        hentSomBruker: Boolean,
    ): List<RSVedtakWrapper> {
        val vedtak = vedtakRepository.findVedtakDbRecordsByIdenter(identer)
        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByIdent(identer)
        val annulleringer = annulleringDAO.finnAnnulleringMedIdent(identer)

        val eksisterendeUtbetalingIder =
            utbetalinger
                .filter { it.utbetalingType == "UTBETALING" || it.utbetalingType == "REVURDERING" }
                .map { it.utbetalingId }

        val vedtakMedUtbetaling =
            vedtak
                .filter { it.utbetalingId != null }
                .filter { eksisterendeUtbetalingIder.contains(it.utbetalingId) }

        fun UtbetalingDbRecord.harAlleVedtak() =
            vedtakMedUtbetaling
                .filter { it.utbetalingId == this.utbetalingId }
                .size == antallVedtak

        fun UtbetalingDbRecord.relaterteVedtak(): List<VedtakDbRecord> =
            vedtakMedUtbetaling
                .filter { it.utbetalingId == this.utbetalingId }
                .sortedBy { it.id }

        fun UtbetalingDbRecord.tilRsVedtakWrapper(): RSVedtakWrapper {
            val vedtakForUtbetaling = relaterteVedtak().map { it.vedtak.tilVedtakFattetForEksternDto() }
            val vedtaket = vedtakForUtbetaling.first()
            val utbetalingen = this.utbetaling.tilUtbetalingUtbetalt()

            return RSVedtakWrapper(
                id = this.id!!,
                annullert = annulleringer.annullererVedtak(vedtaket),
                lest = this.lest != null,
                orgnavn = vedtaket.organisasjonsnummer,
                andreArbeidsgivere = vedtaket.grunnlagForSykepengegrunnlagPerArbeidsgiver,
                lestDato = this.lest?.atZone(ZoneId.of("Europe/Oslo"))?.toOffsetDateTime(),
                opprettetTimestamp = this.opprettet,
                vedtak =
                    RSVedtak(
                        organisasjonsnummer = vedtaket.organisasjonsnummer,
                        dokumenter = vedtakForUtbetaling.flatMap { it.dokumenter },
                        sykepengegrunnlag = vedtaket.sykepengegrunnlag,
                        inntekt = vedtaket.inntekt,
                        fom = vedtakForUtbetaling.minOf { it.fom },
                        tom = vedtakForUtbetaling.maxOf { it.tom },
                        grunnlagForSykepengegrunnlag = vedtaket.grunnlagForSykepengegrunnlag,
                        grunnlagForSykepengegrunnlagPerArbeidsgiver = vedtaket.grunnlagForSykepengegrunnlagPerArbeidsgiver,
                        begrensning = vedtaket.begrensning,
                        vedtakFattetTidspunkt = vedtaket.vedtakFattetTidspunkt,
                        sykepengegrunnlagsfakta = vedtaket.sykepengegrunnlagsfakta,
                        begrunnelser = vedtaket.begrunnelser,
                        tags = vedtaket.tags,
                        utbetaling =
                            RSUtbetalingUtbetalt(
                                utbetalingType = utbetalingen.type,
                                organisasjonsnummer = utbetalingen.organisasjonsnummer,
                                forbrukteSykedager = utbetalingen.forbrukteSykedager,
                                gjenståendeSykedager = utbetalingen.gjenståendeSykedager,
                                foreløpigBeregnetSluttPåSykepenger = utbetalingen.foreløpigBeregnetSluttPåSykepenger,
                                automatiskBehandling = utbetalingen.automatiskBehandling,
                                utbetalingsdager = utbetalingen.utbetalingsdager.map { it.tilRsUtbetalingsdag() },
                                utbetalingId = utbetalingen.utbetalingId,
                                arbeidsgiverOppdrag = utbetalingen.arbeidsgiverOppdrag?.tilRsOppdrag(),
                                personOppdrag = utbetalingen.personOppdrag?.tilRsOppdrag(),
                            ),
                    ),
            )
        }

        return utbetalinger
            .filter { it.harAlleVedtak() }
            .filter { it.skalVisesTilBruker == true || !hentSomBruker }
            .map { it.tilRsVedtakWrapper() }
    }
}

private fun UtbetalingUtbetalt.OppdragDto.tilRsOppdrag(): RSOppdrag =
    RSOppdrag(
        utbetalingslinjer = this.utbetalingslinjer.map { it.tilRsUtbetalingslinje() },
    )

private fun List<RSVedtakWrapper>.markerRevurderte(): List<RSVedtakWrapper> {
    val revurderinger = this.filter { it.vedtak.utbetaling.utbetalingType == "REVURDERING" }

    return this.map {
        val denneErRevurdert =
            revurderinger
                .filter { revurdering -> revurdering.opprettetTimestamp.isAfter(it.opprettetTimestamp) }
                .filter { revurdering -> revurdering.vedtak.organisasjonsnummer == it.vedtak.organisasjonsnummer }
                .any { revurdering -> revurdering.vedtak.overlapper(it.vedtak) }

        if (denneErRevurdert) {
            it.copy(revurdert = true)
        } else {
            it
        }
    }
}

private fun List<Annullering>.annullererVedtak(vedtakDbRecord: VedtakFattetForEksternDto): Boolean {
    return this.any {
        vedtakDbRecord.matcherAnnullering(it)
    }
}

fun VedtakFattetForEksternDto.matcherAnnullering(annullering: Annullering): Boolean {
    val vedtaksperiode = PeriodeImpl(this.fom, this.tom)
    val annulleringsperiode =
        PeriodeImpl(
            annullering.annullering.fom ?: return false,
            annullering.annullering.tom
                ?: return false,
        )
    val annulleringOrgnummer = annullering.annullering.orgnummer ?: annullering.annullering.organisasjonsnummer
    return vedtaksperiode.overlapper(annulleringsperiode) && (this.organisasjonsnummer == annulleringOrgnummer)
}

private fun UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto.tilRsUtbetalingslinje(): RSUtbetalingslinje {
    return RSUtbetalingslinje(
        fom = fom,
        tom = tom,
        dagsats = dagsats,
        totalbeløp = totalbeløp,
        grad = grad,
        stønadsdager = stønadsdager,
    )
}

private fun UtbetalingUtbetalt.UtbetalingdagDto.tilRsUtbetalingsdag(): RSUtbetalingdag {
    return RSUtbetalingdag(
        dato = this.dato,
        type = this.type,
        begrunnelser = this.begrunnelser.map { it.toString() },
    )
}
