package no.nav.helse.flex.service

import no.nav.helse.flex.api.AbstractApiError
import no.nav.helse.flex.api.LogLevel
import no.nav.helse.flex.db.Annullering
import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.PeriodeImpl
import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.RSOppdrag
import no.nav.helse.flex.domene.RSUtbetalingUtbetalt
import no.nav.helse.flex.domene.RSUtbetalingdag
import no.nav.helse.flex.domene.RSUtbetalingslinje
import no.nav.helse.flex.domene.RSVedtak
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.organisasjon.LeggTilOrganisasjonnavn
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.streams.asSequence

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

private fun List<RSVedtakWrapper>.leggTilDagerIVedtakPeriode(): List<RSVedtakWrapper> =
    map { rSVedtakWrapper ->
        val fom = rSVedtakWrapper.vedtak.fom
        val tom = rSVedtakWrapper.vedtak.tom

        var dagerArbeidsgiver =
            hentDager(fom, tom, rSVedtakWrapper.vedtak.utbetaling.arbeidsgiverOppdrag, rSVedtakWrapper.vedtak.utbetaling.utbetalingsdager)
        val sykepengebelopArbeidsgiver = dagerArbeidsgiver.sumOf { it.belop }

        var dagerPerson =
            hentDager(fom, tom, rSVedtakWrapper.vedtak.utbetaling.personOppdrag, rSVedtakWrapper.vedtak.utbetaling.utbetalingsdager)
        val sykepengebelopPerson = dagerPerson.sumOf { it.belop }

        if (sykepengebelopPerson == 0 && sykepengebelopArbeidsgiver == 0) {
            dagerArbeidsgiver = emptyList() // Helt avvist vedtak vises bare i dagerPerson
        } else if (sykepengebelopPerson == 0) {
            dagerPerson = emptyList() // Refusjonutbetaling
        } else if (sykepengebelopArbeidsgiver == 0) {
            dagerArbeidsgiver = emptyList() // Brukerutbetaling
        }

        rSVedtakWrapper.copy(
            dagerArbeidsgiver = dagerArbeidsgiver,
            dagerPerson = dagerPerson,
            sykepengebelopArbeidsgiver = sykepengebelopArbeidsgiver,
            sykepengebelopPerson = sykepengebelopPerson,
        )
    }

private val dagtyperMedUtbetaling = listOf("NavDag", "NavDagSyk", "NavDagDelvisSyk")
private val helg = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

internal fun hentDager(
    fom: LocalDate,
    tom: LocalDate,
    oppdragDto: RSOppdrag?,
    utbetalingsdager: List<RSUtbetalingdag>?,
): List<RSDag> {
    // Setter opp alle dager i perioden
    var dager =
        fom
            .datesUntil(tom.plusDays(1))
            .asSequence()
            .map { dato ->
                RSDag(
                    dato = dato,
                    belop = 0,
                    grad = 0.0,
                    dagtype = if (dato.dayOfWeek in helg) "NavHelgDag" else "NavDag",
                    begrunnelser = emptyList(),
                )
            }
            // Oppdaterer med beløp
            .map { dag ->
                val overlappendeLinjer =
                    oppdragDto
                        ?.utbetalingslinjer
                        ?.filter { linje -> linje.overlapperMed(dag.dato) } // alle linjer som overlapper
                        ?: emptyList()

                overlappendeLinjer.fold(dag) { dagen, linjen ->
                    val utbetalingslinjeUtenUtbetaling = linjen.stønadsdager == 0
                    dagen.copy(
                        belop = if (utbetalingslinjeUtenUtbetaling) 0 else linjen.dagsats,
                        grad = if (utbetalingslinjeUtenUtbetaling) 0.0 else linjen.grad,
                    )
                }
            }
            // Slår sammen med dager fra bømlo
            .associateWith { dag -> utbetalingsdager?.find { it.dato == dag.dato } }
            // Oppdaterer dager med dagtype og begrunnelser
            .map { (dag, utbetalingsdagen) ->
                when (utbetalingsdagen) {
                    null -> dag
                    else ->
                        dag.copy(
                            begrunnelser = utbetalingsdagen.begrunnelser,
                            dagtype =
                                when (utbetalingsdagen.type) {
                                    "NavDag" ->
                                        when {
                                            dag.grad < 100 -> "NavDagDelvisSyk"
                                            else -> "NavDagSyk"
                                        }
                                    "ArbeidsgiverperiodeDag" ->
                                        when {
                                            dag.belop == 0 -> "ArbeidsgiverperiodeDag"
                                            dag.dato.dayOfWeek in helg -> "NavHelgDag" // NAV betaler ikke arbeidsgiverperiode i helg
                                            dag.grad < 100 -> "NavDagDelvisSyk" // Vises som gradert syk
                                            else -> "NavDagSyk" // Vises som 100% syk
                                        }
                                    else -> utbetalingsdagen.type
                                },
                            belop = if (dag.dato.dayOfWeek in helg) 0 else dag.belop,
                            grad = if (dag.dato.dayOfWeek in helg) 0.0 else dag.grad,
                        )
                }
            }.toList()

    val sisteArbeidsgiverperiodeDag = dager.lastOrNull { it.dagtype == "ArbeidsgiverperiodeDag" }
    if (sisteArbeidsgiverperiodeDag?.dato?.dayOfWeek == DayOfWeek.SUNDAY) {
        val overtagelseMandag = utbetalingsdager?.find { it.dato == sisteArbeidsgiverperiodeDag.dato.plusDays(1) }
        if (overtagelseMandag?.type == "ArbeidsgiverperiodeDag") {
            // Dersom nav overtar på mandag så skal ikke helgen før vises som arbeidsgiverperiode
            dager =
                dager.map { dag ->
                    when (dag.dato) {
                        overtagelseMandag.dato.minusDays(2) -> dag.copy(dagtype = "NavHelgDag")
                        overtagelseMandag.dato.minusDays(1) -> dag.copy(dagtype = "NavHelgDag")
                        else -> dag
                    }
                }
        }
    }

    val sisteUtbetalteDag = dager.indexOfLast { it.belop > 0 }
    if (sisteUtbetalteDag == -1) {
        return dager // Ingen dager med utbetaling
    }

    val annenUtbetalingISlutten =
        dager.subList(sisteUtbetalteDag, dager.size).indexOfFirst {
            it.belop == 0 && it.dagtype in dagtyperMedUtbetaling
        }
    if (annenUtbetalingISlutten > -1) {
        // Ligger en person/refusjon utbetaling senere så vi stanser visningen her.
        dager = dager.subList(0, sisteUtbetalteDag + annenUtbetalingISlutten).toList()
    }

    val forsteUtbetalteDag = dager.indexOfFirst { it.belop > 0 }
    val annenUtbetalingIStarten = dager.subList(0, forsteUtbetalteDag).indexOfLast { it.belop == 0 && it.dagtype in dagtyperMedUtbetaling }
    if (annenUtbetalingIStarten > -1) {
        // Ligger en person/refusjon utbetaling tidligere så vi starter visningen her.
        dager = dager.subList(forsteUtbetalteDag, dager.size).toList()
    }

    return dager
}

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

private fun List<Annullering>.annullererVedtak(vedtakDbRecord: VedtakFattetForEksternDto): Boolean =
    this.any {
        vedtakDbRecord.matcherAnnullering(it)
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

private fun UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto.tilRsUtbetalingslinje(): RSUtbetalingslinje =
    RSUtbetalingslinje(
        fom = fom,
        tom = tom,
        dagsats = dagsats,
        totalbeløp = totalbeløp,
        grad = grad,
        stønadsdager = stønadsdager,
    )

private fun UtbetalingUtbetalt.UtbetalingdagDto.tilRsUtbetalingsdag(): RSUtbetalingdag =
    RSUtbetalingdag(
        dato = this.dato,
        type = this.type,
        begrunnelser = this.begrunnelser.map { it.toString() },
    )
