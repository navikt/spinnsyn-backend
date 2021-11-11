package no.nav.helse.flex.service

import no.nav.helse.flex.db.*
import no.nav.helse.flex.domene.*
import no.nav.helse.flex.logger
import no.nav.helse.flex.organisasjon.LeggTilOrganisasjonnavn
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

@Service
class VedtakService(
    private val vedtakRepository: VedtakRepository,
    private val utbetalingRepository: UtbetalingRepository,
    private val retroVedtakService: RetroVedtakService,
    private val annulleringDAO: AnnulleringDAO,
    private val leggTilOrganisasjonavn: LeggTilOrganisasjonnavn,
) {

    val log = logger()

    fun hentVedtak(fnr: String): List<RSVedtakWrapper> {
        val retroVedtak = retroVedtakService.hentVedtak(fnr)

        val nyeVedtak = hentVedtakFraNyeTabeller(fnr)

        val alleVedtak = ArrayList<RSVedtakWrapper>()
            .also {
                it.addAll(retroVedtak)
                it.addAll(nyeVedtak)
            }
            .leggTilDagerIVedtakPeriode()
            .markerRevurderte()
            .leggTilOrgnavn()

        return alleVedtak
    }

    private fun List<RSVedtakWrapper>.leggTilOrgnavn(): List<RSVedtakWrapper> {
        return leggTilOrganisasjonavn.leggTilOrganisasjonnavn(this)
    }

    private fun hentVedtakFraNyeTabeller(fnr: String): List<RSVedtakWrapper> {
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
                        arbeidsgiverOppdrag = RSOppdrag(
                            mottaker = utbetalingen.arbeidsgiverOppdrag.mottaker,
                            nettoBeløp = utbetalingen.arbeidsgiverOppdrag.nettoBeløp,
                            utbetalingslinjer = utbetalingen.arbeidsgiverOppdrag.utbetalingslinjer.map { it.tilRsUtbetalingslinje() }
                        )
                    )
                )
            )
        }

        return utbetalinger
            .filter { it.harAlleVedtak() }
            .map { it.tilRsVedtakWrapper() }
    }
}

private fun List<RSVedtakWrapper>.leggTilDagerIVedtakPeriode(): List<RSVedtakWrapper> {
    return map { rSVedtakWrapper ->
        val fom = rSVedtakWrapper.vedtak.fom
        val tom = rSVedtakWrapper.vedtak.tom
        val helg = listOf(
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        )

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
        rSVedtakWrapper.vedtak.utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.forEach { linje ->
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
        dager = dager.map { dag ->
            rSVedtakWrapper.vedtak.utbetaling.utbetalingsdager
                .find { it.dato == dag.dato }
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

        // Dager med utbetaling
        val stønadsdager = dager.filter {
            it.dagtype in listOf("NavDag", "NavDagSyk", "NavDagDelvisSyk")
        }
        rSVedtakWrapper.copy(
            dager = dager,
            dagligUtbetalingsbelop = stønadsdager.maxOfOrNull { it.belop } ?: 0,
            antallDagerMedUtbetaling = stønadsdager.size,
            sykepengebelop = stønadsdager.sumOf { it.belop },
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
