package no.nav.helse.flex.util

import no.nav.helse.flex.db.Annullering
import no.nav.helse.flex.domene.*
import no.nav.helse.flex.domene.PeriodeImpl
import no.nav.helse.flex.logger
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.streams.asSequence

private val dagtyperMedUtbetaling = listOf("NavDag", "NavDagSyk", "NavDagDelvisSyk")
private val helg = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun RSVedtakWrapper.leggTilDagerIVedtakPeriode(): RSVedtakWrapper {
    val fom = this.vedtak.fom
    val tom = this.vedtak.tom

    var dagerArbeidsgiver =
        hentDager(fom, tom, this.vedtak.utbetaling.arbeidsgiverOppdrag, this.vedtak.utbetaling.utbetalingsdager)
    val sykepengebelopArbeidsgiver = dagerArbeidsgiver.sumOf { it.belop }

    var dagerPerson =
        hentDager(fom, tom, this.vedtak.utbetaling.personOppdrag, this.vedtak.utbetaling.utbetalingsdager)
    val sykepengebelopPerson = dagerPerson.sumOf { it.belop }

    if (sykepengebelopPerson == 0 && sykepengebelopArbeidsgiver == 0) {
        dagerArbeidsgiver = emptyList() // Helt avvist vedtak vises bare i dagerPerson
    } else if (sykepengebelopPerson == 0) {
        dagerPerson = emptyList() // Refusjonutbetaling
    } else if (sykepengebelopArbeidsgiver == 0) {
        dagerArbeidsgiver = emptyList() // Brukerutbetaling
    }
    val utbetalingsdager =
        korrigerUtbetalingsdager(
            utbetalingsdager = this.vedtak.utbetaling.utbetalingsdager,
            fom = vedtak.fom,
            tom = vedtak.tom,
        )
    val (daglisteSykmeldt, daglisteArbeidsgiver) = splittDaglister(utbetalingsdager)

    return this.copy(
        dagerArbeidsgiver = dagerArbeidsgiver,
        dagerPerson = dagerPerson,
        sykepengebelopArbeidsgiver = sykepengebelopArbeidsgiver,
        sykepengebelopPerson = sykepengebelopPerson,
        daglisteSykmeldt = daglisteSykmeldt.mapTil(erSykmeldt = true),
        daglisteArbeidsgiver = daglisteArbeidsgiver.mapTil(erSykmeldt = false),
    )
}

fun splittDaglister(utbetalingsdager: List<RSUtbetalingdag>): Pair<List<RSUtbetalingdag>, List<RSUtbetalingdag>> {
    if (utbetalingsdager.isEmpty()) return Pair(emptyList(), emptyList())

    val dagerMedBelopSykmeldt = utbetalingsdager.filter { it.beløpTilSykmeldt != null && it.beløpTilSykmeldt > 0 }
    val dagerMedBelopArbeidsgiver =
        utbetalingsdager.filter { it.beløpTilArbeidsgiver != null && it.beløpTilArbeidsgiver > 0 }

    if (dagerMedBelopArbeidsgiver.isEmpty()) return Pair(utbetalingsdager, emptyList())
    if (dagerMedBelopSykmeldt.isEmpty()) return Pair(emptyList(), utbetalingsdager)

    return fordelDager(dagerMedBelopSykmeldt, dagerMedBelopArbeidsgiver, utbetalingsdager)
}

private fun fordelDager(
    dagerMedBelopSykmeldt: List<RSUtbetalingdag>,
    dagerMedBelopArbeidsgiver: List<RSUtbetalingdag>,
    utbetalingsdager: List<RSUtbetalingdag>,
): Pair<List<RSUtbetalingdag>, List<RSUtbetalingdag>> {
    val periodeSykmeldt = finnPerioder(dagerMedBelopSykmeldt)
    val periodeArbeidsgiver = finnPerioder(dagerMedBelopArbeidsgiver)

    val erSykmeldtPeriodeTidligst = periodeSykmeldt.fom <= periodeArbeidsgiver.fom
    val erSykmeldtPeriodeSenest = periodeSykmeldt.tom >= periodeArbeidsgiver.tom
    val tidligsteFom = minOf(periodeSykmeldt.fom, periodeArbeidsgiver.fom)
    val senesteTom = maxOf(periodeSykmeldt.tom, periodeArbeidsgiver.tom)

    val dagerFørFom = utbetalingsdager.filter { it.dato < tidligsteFom }
    val dagerEtterTom = utbetalingsdager.filter { it.dato > senesteTom }

    val dagerSykmeldt = utbetalingsdager.filter { periodeSykmeldt.inneholderDato(it.dato) }.toMutableList()
    val dagerArbeidsgiver = utbetalingsdager.filter { periodeArbeidsgiver.inneholderDato(it.dato) }.toMutableList()

    if (erSykmeldtPeriodeTidligst) {
        dagerSykmeldt += dagerFørFom
    } else {
        dagerArbeidsgiver += dagerFørFom
    }

    if (erSykmeldtPeriodeSenest) {
        dagerSykmeldt += dagerEtterTom
    } else {
        dagerArbeidsgiver += dagerEtterTom
    }

    return Pair(dagerSykmeldt.sortedBy { it.dato }, dagerArbeidsgiver.sortedBy { it.dato })
}

private fun List<RSUtbetalingdag>.mapTil(erSykmeldt: Boolean): List<RSDagV2> =
    this.map {
        RSDagV2(
            dato = it.dato,
            belop = (if (erSykmeldt) it.beløpTilSykmeldt else it.beløpTilArbeidsgiver) ?: 0,
            grad = it.sykdomsgrad ?: 0,
            dagtype = it.type,
            begrunnelser = it.begrunnelser,
        )
    }

private fun finnPerioder(dager: List<RSUtbetalingdag>): PeriodeImpl =
    PeriodeImpl(dager.minBy { it.dato }.dato, dager.maxBy { it.dato }.dato)

internal fun korrigerUtbetalingsdager(
    utbetalingsdager: List<RSUtbetalingdag>?,
    fom: LocalDate,
    tom: LocalDate,
): List<RSUtbetalingdag> =
    utbetalingsdager
        ?.filter { it.dato in fom..tom }
        ?.map { it.korrigerArbeidsgiverperiode() }
        ?.fiksHelgFoerOvertagelse()
        ?: emptyList()

private fun RSUtbetalingdag.korrigerArbeidsgiverperiode(): RSUtbetalingdag {
    val harBeløp = beløpTilArbeidsgiver != null && beløpTilSykmeldt != null
    if (!harBeløp) {
        logger().warn("Utbetalingsdag ${this.dato} mangler beløp, kan ikke korrigere for arbeidsgiverperiode")
        return this
    }
    val erUtbetaling = beløpTilArbeidsgiver != 0 || beløpTilSykmeldt != 0
    return if (type == "ArbeidsgiverperiodeDag" && erUtbetaling) {
        if (dato.dayOfWeek in helg) {
            copy(type = "NavHelgDag", beløpTilArbeidsgiver = 0, beløpTilSykmeldt = 0, sykdomsgrad = 0)
        } else {
            copy(type = "NavDag")
        }
    } else {
        this
    }
}

// Dersom nav overtar på mandag så skal ikke helgen før vises som arbeidsgiverperiode
private fun List<RSUtbetalingdag>.fiksHelgFoerOvertagelse(): List<RSUtbetalingdag> {
    val sisteArbeidsgiverperiodeDag = this.lastOrNull { it.type == "ArbeidsgiverperiodeDag" }
    if (sisteArbeidsgiverperiodeDag?.dato?.dayOfWeek == DayOfWeek.SUNDAY) {
        val overtagelseMandag = this.find { it.dato.equals(sisteArbeidsgiverperiodeDag.dato.plusDays(1)) }
        if (overtagelseMandag != null && overtagelseMandag.type != "ArbeidsgiverperiodeDag") {
            return this.map { dag ->
                when (dag.dato) {
                    overtagelseMandag.dato.minusDays(2) -> dag.copy(type = "NavHelgDag")
                    overtagelseMandag.dato.minusDays(1) -> dag.copy(type = "NavHelgDag")
                    else -> dag
                }
            }
        }
    }
    return this
}

fun List<RSVedtakWrapper>.leggTilDagerIVedtakPeriode(): List<RSVedtakWrapper> =
    this.map {
        it.leggTilDagerIVedtakPeriode()
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
                    null -> {
                        dag
                    }

                    else -> {
                        dag.copy(
                            begrunnelser = utbetalingsdagen.begrunnelser,
                            dagtype =
                                when (utbetalingsdagen.type) {
                                    "NavDag" -> {
                                        when {
                                            dag.grad < 100 -> "NavDagDelvisSyk"
                                            else -> "NavDagSyk"
                                        }
                                    }

                                    "ArbeidsgiverperiodeDag" -> {
                                        when {
                                            dag.belop == 0 -> "ArbeidsgiverperiodeDag"

                                            // NAV betaler ikke arbeidsgiverperiode i helg
                                            dag.dato.dayOfWeek in helg -> "NavHelgDag"

                                            // Vises som gradert syk
                                            dag.grad < 100 -> "NavDagDelvisSyk"

                                            // Vises som 100% syk
                                            else -> "NavDagSyk"
                                        }
                                    }

                                    else -> {
                                        utbetalingsdagen.type
                                    }
                                },
                            belop = if (dag.dato.dayOfWeek in helg) 0 else dag.belop,
                            grad = if (dag.dato.dayOfWeek in helg) 0.0 else dag.grad,
                        )
                    }
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
        beløpTilArbeidsgiver = this.beløpTilArbeidsgiver,
        beløpTilSykmeldt = this.beløpTilSykmeldt,
        sykdomsgrad = this.sykdomsgrad,
    )
