package no.nav.helse.flex.util

import no.nav.helse.flex.db.Annullering
import no.nav.helse.flex.domene.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.streams.asSequence

private val dagtyperMedUtbetaling = listOf("NavDag", "NavDagSyk", "NavDagDelvisSyk")
private val helg = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun List<RSVedtakWrapper>.leggTilDagerIVedtakPeriode(): List<RSVedtakWrapper> =
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

fun hentDager(
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

fun List<RSVedtakWrapper>.markerRevurderte(): List<RSVedtakWrapper> {
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

fun List<Annullering>.annullererVedtak(vedtakDbRecord: VedtakFattetForEksternDto): Boolean =
    this.any {
        vedtakDbRecord.matcherAnnullering(it)
    }

fun VedtakFattetForEksternDto.matcherAnnullering(annullering: Annullering): Boolean {
    val vedtaksperiode = PeriodeImpl(this.fom, this.tom)
    val annulleringsperiode =
        PeriodeImpl(
            annullering.annullering.fom ?: return false,
            annullering.annullering.tom ?: return false,
        )
    val annulleringOrgnummer = annullering.annullering.orgnummer ?: annullering.annullering.organisasjonsnummer
    return vedtaksperiode.overlapper(annulleringsperiode) && (this.organisasjonsnummer == annulleringOrgnummer)
}

fun UtbetalingUtbetalt.OppdragDto.tilRsOppdrag(): RSOppdrag =
    RSOppdrag(
        utbetalingslinjer = this.utbetalingslinjer.map { it.tilRsUtbetalingslinje() },
    )

fun UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto.tilRsUtbetalingslinje(): RSUtbetalingslinje =
    RSUtbetalingslinje(
        fom = fom,
        tom = tom,
        dagsats = dagsats,
        totalbeløp = totalbeløp,
        grad = grad,
        stønadsdager = stønadsdager,
    )

fun UtbetalingUtbetalt.UtbetalingdagDto.tilRsUtbetalingsdag(): RSUtbetalingdag =
    RSUtbetalingdag(
        dato = this.dato,
        type = this.type,
        begrunnelser = this.begrunnelser.map { it.toString() },
    )
