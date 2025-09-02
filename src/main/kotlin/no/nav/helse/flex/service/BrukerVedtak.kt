package no.nav.helse.flex.service

import no.nav.helse.flex.api.AbstractApiError
import no.nav.helse.flex.api.LogLevel
import no.nav.helse.flex.db.Annullering
import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.RSUtbetalingUtbetalt
import no.nav.helse.flex.domene.RSVedtak
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.organisasjon.LeggTilOrganisasjonnavn
import no.nav.helse.flex.util.annullererVedtak
import no.nav.helse.flex.util.leggTilDagerIVedtakPeriode
import no.nav.helse.flex.util.markerRevurderte
import no.nav.helse.flex.util.tilRsOppdrag
import no.nav.helse.flex.util.tilRsUtbetalingsdag
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

    class VedtakIkkeFunnetException(
        vedtaksId: String,
    ) : AbstractApiError(
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
            .leggTilDagerIVedtakPeriode()
            .markerRevurderte()
            .map { it.fjernArbeidIkkeGjenopptattDager() }
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

    internal fun mapTilRsVedtakWrapper(
        utbetalingDbRecord: UtbetalingDbRecord,
        vedtakMedUtbetaling: List<VedtakDbRecord>,
        annulleringer: List<Annullering>,
    ): RSVedtakWrapper {
        val vedtakForUtbetaling =
            hentRelaterteVedtak(
                utbetalingDbRecord,
                vedtakMedUtbetaling,
            ).map { it.vedtak.tilVedtakFattetForEksternDto() }
        val vedtaket = vedtakForUtbetaling.first()
        val utbetalingen = utbetalingDbRecord.utbetaling.tilUtbetalingUtbetalt()

        return RSVedtakWrapper(
            id = utbetalingDbRecord.id!!,
            annullert = annulleringer.annullererVedtak(vedtaket),
            lest = utbetalingDbRecord.lest != null,
            orgnavn = vedtaket.organisasjonsnummer,
            andreArbeidsgivere = vedtaket.grunnlagForSykepengegrunnlagPerArbeidsgiver,
            lestDato = utbetalingDbRecord.lest?.atZone(ZoneId.of("Europe/Oslo"))?.toOffsetDateTime(),
            opprettetTimestamp = utbetalingDbRecord.opprettet,
            vedtak =
                RSVedtak(
                    organisasjonsnummer = vedtaket.organisasjonsnummer,
                    yrkesaktivitetstype = vedtaket.yrkesaktivitetstype ?: "ARBEIDSTAKER",
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

    internal fun harAlleVedtak(
        utbetalingDbRecord: UtbetalingDbRecord,
        vedtakMedUtbetaling: List<VedtakDbRecord>,
    ): Boolean =
        vedtakMedUtbetaling
            .filter { it.utbetalingId == utbetalingDbRecord.utbetalingId }
            .size == utbetalingDbRecord.antallVedtak

    internal fun hentRelaterteVedtak(
        utbetalingDbRecord: UtbetalingDbRecord,
        vedtakMedUtbetaling: List<VedtakDbRecord>,
    ): List<VedtakDbRecord> =
        vedtakMedUtbetaling
            .filter { it.utbetalingId == utbetalingDbRecord.utbetalingId }
            .sortedBy { it.id }

    private fun finnAlleVedtak(
        identer: List<String>,
        hentSomBruker: Boolean,
    ): List<RSVedtakWrapper> {
        val vedtak = vedtakRepository.findVedtakDbRecordsByIdenter(identer)
        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByIdent(identer)
        val annulleringer = annulleringDAO.finnAnnulleringMedIdent(identer)

        val vedtakMedUtbetaling =
            vedtak
                .filter { it.utbetalingId != null }
                .filter { utbetaling ->
                    utbetalinger.any {
                        it.utbetalingId == utbetaling.utbetalingId &&
                            (it.utbetalingType == "UTBETALING" || it.utbetalingType == "REVURDERING")
                    }
                }

        return utbetalinger
            .filter { it.utbetalingType == "UTBETALING" || it.utbetalingType == "REVURDERING" }
            .filter { harAlleVedtak(it, vedtakMedUtbetaling) }
            .filter { it.skalVisesTilBruker == true || !hentSomBruker }
            .map { mapTilRsVedtakWrapper(it, vedtakMedUtbetaling, annulleringer) }
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

    private fun List<RSVedtakWrapper>.leggTilOrgnavn(): List<RSVedtakWrapper> = leggTilOrganisasjonavn.leggTilOrganisasjonnavn(this)

    private fun List<RSVedtakWrapper>.leggTilArbeidsgivere(): List<RSVedtakWrapper> = leggTilOrganisasjonavn.leggTilAndreArbeidsgivere(this)
}
