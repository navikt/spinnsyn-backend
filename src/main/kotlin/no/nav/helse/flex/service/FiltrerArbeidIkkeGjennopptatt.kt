package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSVedtakWrapper
import java.time.LocalDate

fun RSVedtakWrapper.fjernArbeidIkkeGjenopptattDager(): RSVedtakWrapper {
    val dagerArbeidsgiver = this.dagerArbeidsgiver.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }
    val dagerPerson = this.dagerPerson.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }

    val utbetalingsdager =
        this.vedtak.utbetaling.utbetalingsdager
            ?.filter { it.type != "ArbeidIkkeGjenopptattDag" } ?: emptyList()

    fun finnFaktiskFom(): LocalDate {
        val tidligstedag = (dagerArbeidsgiver + dagerPerson).minByOrNull { it.dato }
        return tidligstedag?.dato ?: this.vedtak.fom
    }

    fun finnFaktiskFomUtbetalingsdager(): LocalDate {
        val tidligstedag = utbetalingsdager.minByOrNull { it.dato }
        return tidligstedag?.dato ?: this.vedtak.fom
    }

    val faktiskFomUtbetalingsdager = finnFaktiskFomUtbetalingsdager()
    val faktiskFom = finnFaktiskFom()

    if (!faktiskFomUtbetalingsdager.equals(faktiskFom)) {
        throw IllegalStateException(
            "Faktisk fom fra utbetalingsdager er forskjellig fra faktisk fom fra dagerArbeidsgiver/dagerPerson. " +
                "faktiskFomUtbetalingsdager=$faktiskFomUtbetalingsdager, faktiskFom=$faktiskFom",
        )
    }

    return this.copy(
        dagerArbeidsgiver = dagerArbeidsgiver,
        dagerPerson = dagerPerson,
        vedtak =
            this.vedtak.copy(
                fom = faktiskFomUtbetalingsdager,
                utbetaling =
                    this.vedtak.utbetaling.copy(
                        utbetalingsdager = utbetalingsdager,
                    ),
            ),
    )
}
