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

@Service
class BrukerVedtak(
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
        ALDRI_SENDT_BRUKERNOTIFIKASJON,
    }

    class VedtakIkkeFunnetException(vedtaksId: String) : AbstractApiError(
        message = "Fant ikke vedtak $vedtaksId",
        httpStatus = HttpStatus.NOT_FOUND,
        reason = "VEDTAK_IKKE_FUNNET",
        loglevel = LogLevel.WARN
    )

    fun hentVedtak(fnr: String): List<RSVedtakWrapper> {
        return finnAlleVedtak(fnr)
            .leggTilDagerIVedtakPeriode()
            .markerRevurderte()
            .leggTilOrgnavn()
            .leggTilArbeidsgivere()
    }

    fun lesVedtak(fnr: String, vedtaksId: String): String {
        val (lesUtbetaling, _) = lesUtbetaling(fnr = fnr, utbetalingsId = vedtaksId)

        if (lesUtbetaling == LesResultat.IKKE_FUNNET) {
            throw VedtakIkkeFunnetException(vedtaksId)
        }

        if (lesUtbetaling == LesResultat.ALLEREDE_LEST) {
            return "Vedtak $vedtaksId er allerede lest"
        }

        vedtakStatusProducer.produserMelding(
            VedtakStatusDTO(fnr = fnr, id = vedtaksId, vedtakStatus = VedtakStatus.LEST)
        )

        utbetalingRepository.updateLestByFnrAndId(
            lest = Instant.now(),
            fnr = fnr,
            id = vedtaksId
        )

        return "Leste vedtak $vedtaksId"
    }

    private fun lesUtbetaling(fnr: String, utbetalingsId: String): Pair<LesResultat, String?> {
        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .find { it.id == utbetalingsId }
            ?: return LesResultat.IKKE_FUNNET to null

        if (utbetalingDbRecord.lest != null) {
            return LesResultat.ALLEREDE_LEST to null
        }

        if (utbetalingDbRecord.brukernotifikasjonSendt == null) {
            return LesResultat.ALDRI_SENDT_BRUKERNOTIFIKASJON to null
        }

        return LesResultat.LEST to utbetalingDbRecord.varsletMed
    }

    private fun List<RSVedtakWrapper>.leggTilOrgnavn(): List<RSVedtakWrapper> {
        return leggTilOrganisasjonavn.leggTilOrganisasjonnavn(this)
    }

    private fun List<RSVedtakWrapper>.leggTilArbeidsgivere(): List<RSVedtakWrapper> {
        return leggTilOrganisasjonavn.leggTilAndreArbeidsgivere(this)
    }

    private fun finnAlleVedtak(fnr: String): List<RSVedtakWrapper> {
        val vedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr)
        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        val annulleringer = annulleringDAO.finnAnnullering(fnr)

        val eksisterendeUtbetalingIder = utbetalinger
            .filter { it.utbetalingType == "UTBETALING" || it.utbetalingType == "REVURDERING" }
            .map { it.utbetalingId }

        val vedtakMedUtbetaling = vedtak
            .filter { it.utbetalingId != null }
            .filter { eksisterendeUtbetalingIder.contains(it.utbetalingId) }

        fun UtbetalingDbRecord.harAlleVedtak() = vedtakMedUtbetaling
            .filter { it.utbetalingId == this.utbetalingId }
            .size == antallVedtak

        fun UtbetalingDbRecord.relaterteVedtak(): List<VedtakDbRecord> = vedtakMedUtbetaling
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
                opprettet = LocalDate.ofInstant(this.opprettet, ZoneId.of("Europe/Oslo")),
                vedtak = RSVedtak(
                    organisasjonsnummer = vedtaket.organisasjonsnummer,
                    dokumenter = vedtakForUtbetaling.flatMap { it.dokumenter },
                    sykepengegrunnlag = vedtaket.sykepengegrunnlag,
                    inntekt = vedtaket.inntekt,
                    fom = vedtakForUtbetaling.minOf { it.fom },
                    tom = vedtakForUtbetaling.maxOf { it.tom },
                    grunnlagForSykepengegrunnlag = vedtaket.grunnlagForSykepengegrunnlag,
                    grunnlagForSykepengegrunnlagPerArbeidsgiver = vedtaket.grunnlagForSykepengegrunnlagPerArbeidsgiver,
                    begrensning = vedtaket.begrensning,
                    utbetaling = RSUtbetalingUtbetalt(
                        utbetalingType = utbetalingen.type,
                        organisasjonsnummer = utbetalingen.organisasjonsnummer,
                        forbrukteSykedager = utbetalingen.forbrukteSykedager,
                        gjenståendeSykedager = utbetalingen.gjenståendeSykedager,
                        foreløpigBeregnetSluttPåSykepenger = utbetalingen.foreløpigBeregnetSluttPåSykepenger,
                        automatiskBehandling = utbetalingen.automatiskBehandling,
                        utbetalingsdager = utbetalingen.utbetalingsdager.map { it.tilRsUtbetalingsdag() },
                        utbetalingId = utbetalingen.utbetalingId,
                        arbeidsgiverOppdrag = utbetalingen.arbeidsgiverOppdrag?.tilRsOppdrag(),
                        personOppdrag = utbetalingen.personOppdrag?.tilRsOppdrag()
                    )
                )
            )
        }

        return utbetalinger
            .filter { it.harAlleVedtak() }
            .filter { it.skalVisesTilBruker != false }
            .map { it.tilRsVedtakWrapper() }
    }
}

private fun UtbetalingUtbetalt.OppdragDto.tilRsOppdrag(): RSOppdrag = RSOppdrag(
    mottaker = this.mottaker,
    nettoBeløp = this.nettoBeløp,
    utbetalingslinjer = this.utbetalingslinjer.map { it.tilRsUtbetalingslinje() }
)

private fun List<RSVedtakWrapper>.leggTilDagerIVedtakPeriode(): List<RSVedtakWrapper> {
    return map { rSVedtakWrapper ->
        val fom = rSVedtakWrapper.vedtak.fom
        val tom = rSVedtakWrapper.vedtak.tom
        val helg = listOf(
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        )

        fun hentDager(oppdragDto: RSOppdrag?): List<RSDag> {
            // Setter opp alle dager i perioden
            var dager = fom.datesUntil(tom.plusDays(1))
                .map { dato ->
                    RSDag(
                        dato = dato,
                        belop = 0,
                        grad = 0.0,
                        dagtype = if (dato.dayOfWeek in helg) "NavHelgDag" else "NavDag",
                        begrunnelser = emptyList()
                    )
                }.toList()

            // Oppdaterer dager med beløp og grad
            oppdragDto?.utbetalingslinjer?.forEach { linje ->
                val periode = linje.fom..linje.tom
                val utbetalingslinjeUtenUtbetaling = linje.stønadsdager == 0

                dager = dager.map OppdaterBelopOgGrad@{ dag ->
                    if (dag.dato in periode && dag.dato.dayOfWeek !in helg) {
                        if (utbetalingslinjeUtenUtbetaling) {
                            return@OppdaterBelopOgGrad dag.copy(
                                belop = 0,
                                grad = 0.0
                            )
                        } else {
                            return@OppdaterBelopOgGrad dag.copy(
                                belop = linje.dagsats,
                                grad = linje.grad
                            )
                        }
                    }
                    return@OppdaterBelopOgGrad dag
                }
            }

            // Oppdater dager med dagtype og begrunnelser
            dager = dager
                .map { dag ->
                    rSVedtakWrapper.vedtak.utbetaling.utbetalingsdager
                        ?.find { it.dato == dag.dato }
                        ?.let {
                            dag.copy(
                                begrunnelser = it.begrunnelser,
                                dagtype = if (it.type == "NavDag" && dag.grad != 100.0) {
                                    "NavDagDelvisSyk"
                                } else if (it.type == "NavDag") {
                                    "NavDagSyk"
                                } else {
                                    it.type
                                }
                            )
                        }
                        ?: dag
                }
                .filter { !((it.dagtype.startsWith("NavDag") && it.belop == 0)) }
            return dager ?: emptyList()
        }

        // Dager med arbeidsgiverutbetaling
        val dagerArbeidsgiver = hentDager(rSVedtakWrapper.vedtak.utbetaling.arbeidsgiverOppdrag)

        val stønadsdagerArbeidsgiver = dagerArbeidsgiver.filter {
            it.dagtype in listOf("NavDag", "NavDagSyk", "NavDagDelvisSyk")
        }
        // Persondager med utbetaling
        val dagerPerson = hentDager(rSVedtakWrapper.vedtak.utbetaling.personOppdrag)

        val stønadsdagerPerson = dagerPerson.filter {
            it.dagtype in listOf("NavDag", "NavDagSyk", "NavDagDelvisSyk")
        }
        rSVedtakWrapper.copy(
            dager = dagerArbeidsgiver, // Deprecated
            dagerArbeidsgiver = dagerArbeidsgiver,
            dagerPerson = dagerPerson,
            sykepengebelop = stønadsdagerArbeidsgiver.sumOf { it.belop }, // Deprecated
            sykepengebelopArbeidsgiver = stønadsdagerArbeidsgiver.sumOf { it.belop },
            sykepengebelopPerson = stønadsdagerPerson.sumOf { it.belop },
        )
    }
}

private fun List<RSVedtakWrapper>.markerRevurderte(): List<RSVedtakWrapper> {

    val revurderinger = this.filter { it.vedtak.utbetaling.utbetalingType == "REVURDERING" }

    return this.map {

        val denneErRevurdert = revurderinger
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
    val annulleringsperiode = PeriodeImpl(
        annullering.annullering.fom ?: return false,
        annullering.annullering.tom
            ?: return false
    )
    return vedtaksperiode.overlapper(annulleringsperiode) && (this.organisasjonsnummer == annullering.annullering.orgnummer)
}

private fun UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto.tilRsUtbetalingslinje(): RSUtbetalingslinje {
    return RSUtbetalingslinje(
        fom = fom,
        tom = tom,
        dagsats = dagsats,
        totalbeløp = totalbeløp,
        grad = grad,
        stønadsdager = stønadsdager,
        dagsatsTransformasjonHjelper = dagsats
    )
}

private fun UtbetalingUtbetalt.UtbetalingdagDto.tilRsUtbetalingsdag(): RSUtbetalingdag {
    return RSUtbetalingdag(
        dato = this.dato,
        type = this.type,
        begrunnelser = this.begrunnelser
    )
}
