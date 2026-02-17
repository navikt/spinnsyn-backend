package no.nav.helse.flex.util

import no.nav.helse.flex.domene.RSUtbetalingdag
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VedtakUtilTest {
    @Test
    fun `arbeidsgiverperiode med 0-beløp på ukedag forblir arbeidsgiverperiode`() {
        val dag =
            RSUtbetalingdag(
                dato = LocalDate.of(2024, 2, 5),
                type = "ArbeidsgiverperiodeDag",
                beløpTilArbeidsgiver = 0,
                beløpTilSykmeldt = 0,
                sykdomsgrad = 100,
                begrunnelser = emptyList(),
            )
        val korrigert = korrigerUtbetalingsdager(utbetalingsdager = listOf(dag), fom = dag.dato, tom = dag.dato).first()
        korrigert.type shouldBeEqualTo "ArbeidsgiverperiodeDag"
    }

    @Test
    fun `arbeidsgiverperiode med beløpTilArbeidsgiver på ukedag blir NavDagSyk`() {
        val dag =
            RSUtbetalingdag(
                dato = LocalDate.of(2024, 2, 6),
                type = "ArbeidsgiverperiodeDag",
                beløpTilArbeidsgiver = 100,
                beløpTilSykmeldt = 0,
                sykdomsgrad = 100,
                begrunnelser = emptyList(),
            )
        val korrigert = korrigerUtbetalingsdager(utbetalingsdager = listOf(dag), fom = dag.dato, tom = dag.dato).first()
        korrigert.type shouldBeEqualTo "NavDag"
    }

    @Test
    fun `arbeidsgiverperiode med beløpTilSykmeldt på ukedag blir NavDagSyk`() {
        val dag =
            RSUtbetalingdag(
                dato = LocalDate.of(2024, 2, 6),
                type = "ArbeidsgiverperiodeDag",
                beløpTilArbeidsgiver = 0,
                beløpTilSykmeldt = 100,
                sykdomsgrad = 100,
                begrunnelser = emptyList(),
            )
        val korrigert = korrigerUtbetalingsdager(utbetalingsdager = listOf(dag), fom = dag.dato, tom = dag.dato).first()
        korrigert.type shouldBeEqualTo "NavDag"
    }

    @Test
    fun `arbeidsgiverperiode med beløp på helg blir NavHelgDag`() {
        val lordag = LocalDate.of(2024, 2, 10)
        val dag =
            RSUtbetalingdag(
                dato = lordag,
                type = "ArbeidsgiverperiodeDag",
                beløpTilArbeidsgiver = 100,
                beløpTilSykmeldt = 0,
                sykdomsgrad = 100,
                begrunnelser = emptyList(),
            )
        val korrigert = korrigerUtbetalingsdager(utbetalingsdager = listOf(dag), fom = dag.dato, tom = dag.dato).first()
        korrigert.type shouldBeEqualTo "NavHelgDag"
        korrigert.beløpTilArbeidsgiver shouldBeEqualTo 0
        korrigert.beløpTilSykmeldt shouldBeEqualTo 0
        korrigert.sykdomsgrad shouldBeEqualTo 0
    }

    @Test
    fun `ArbeidsgiverperiodeDag på helg blir NavHelgDag`() {
        val sondag = LocalDate.of(2024, 2, 11)
        val dag =
            RSUtbetalingdag(
                dato = sondag,
                type = "ArbeidsgiverperiodeDag",
                beløpTilArbeidsgiver = 100,
                beløpTilSykmeldt = 100,
                sykdomsgrad = 100,
                begrunnelser = emptyList(),
            )
        val korrigert = korrigerUtbetalingsdager(utbetalingsdager = listOf(dag), fom = dag.dato, tom = dag.dato).first()
        korrigert.type shouldBeEqualTo "NavHelgDag"
        korrigert.beløpTilArbeidsgiver shouldBeEqualTo 0
        korrigert.beløpTilSykmeldt shouldBeEqualTo 0
        korrigert.sykdomsgrad shouldBeEqualTo 0
    }

    @Test
    fun `NavDag på ukedag forblir NavDag`() {
        val dag =
            RSUtbetalingdag(
                dato = LocalDate.of(2024, 2, 7),
                type = "NavDag",
                beløpTilArbeidsgiver = 100,
                beløpTilSykmeldt = 100,
                sykdomsgrad = 100,
                begrunnelser = emptyList(),
            )
        val korrigert = korrigerUtbetalingsdager(utbetalingsdager = listOf(dag), fom = dag.dato, tom = dag.dato).first()
        korrigert.type shouldBeEqualTo "NavDag"
        korrigert.beløpTilArbeidsgiver shouldBeEqualTo 100
        korrigert.beløpTilSykmeldt shouldBeEqualTo 100
        korrigert.sykdomsgrad shouldBeEqualTo 100
    }

    @Test
    fun `Utbetalingsdag utenfor vedtaksperiode blir ikke med i dagliste`() {
        val dag =
            RSUtbetalingdag(
                dato = LocalDate.of(2024, 2, 7),
                type = "NavDag",
                beløpTilArbeidsgiver = 100,
                beløpTilSykmeldt = 100,
                sykdomsgrad = 100,
                begrunnelser = emptyList(),
            )
        val korrigert =
            korrigerUtbetalingsdager(
                utbetalingsdager = listOf(dag),
                fom = LocalDate.of(2024, 2, 8),
                tom = LocalDate.of(2024, 2, 10),
            )
        korrigert shouldBeEqualTo emptyList()
    }

    @Test
    fun `Utbetalingsdag med null beløp blir ikke korrigert`() {
        val dag =
            RSUtbetalingdag(
                dato = LocalDate.of(2024, 2, 7),
                type = "NavDag",
                beløpTilArbeidsgiver = null,
                beløpTilSykmeldt = null,
                sykdomsgrad = null,
                begrunnelser = emptyList(),
            )
        val korrigert =
            korrigerUtbetalingsdager(
                utbetalingsdager = listOf(dag),
                fom = dag.dato,
                tom = dag.dato,
            ).first()
        korrigert.type shouldBeEqualTo "NavDag"
        korrigert.beløpTilArbeidsgiver shouldBeEqualTo null
        korrigert.beløpTilSykmeldt shouldBeEqualTo null
        korrigert.sykdomsgrad shouldBeEqualTo null
    }

    @Test
    fun `Håndterer arbeidsgiverperiode i helg der Nav overtar utbetaling på mandagen`() {
        val lordag = LocalDate.of(2024, 2, 10)
        val sondag = LocalDate.of(2024, 2, 11)
        val mandag = LocalDate.of(2024, 2, 12)

        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(
                    dato = lordag,
                    type = "ArbeidsgiverperiodeDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = sondag,
                    type = "ArbeidsgiverperiodeDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = mandag,
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
            )

        val korrigert =
            korrigerUtbetalingsdager(
                utbetalingsdager = utbetalingsdager,
                fom = lordag,
                tom = mandag,
            )

        korrigert[0].type shouldBeEqualTo "NavHelgDag"
        korrigert[0].beløpTilArbeidsgiver shouldBeEqualTo 0
        korrigert[0].beløpTilSykmeldt shouldBeEqualTo 0
        korrigert[0].sykdomsgrad shouldBeEqualTo 0

        korrigert[1].type shouldBeEqualTo "NavHelgDag"
        korrigert[1].beløpTilArbeidsgiver shouldBeEqualTo 0
        korrigert[1].beløpTilSykmeldt shouldBeEqualTo 0
        korrigert[1].sykdomsgrad shouldBeEqualTo 0

        korrigert[2].type shouldBeEqualTo "NavDag"
        korrigert[2].beløpTilArbeidsgiver shouldBeEqualTo 0
        korrigert[2].beløpTilSykmeldt shouldBeEqualTo 100
        korrigert[2].sykdomsgrad shouldBeEqualTo 100
    }

    @Test
    fun `splittDaglister returnerer tomme lister når input er tom`() {
        val resultat = splittDaglister(emptyList())

        resultat.first shouldBeEqualTo emptyList()
        resultat.second shouldBeEqualTo emptyList()
    }

    @Test
    fun `splittDaglister returnerer alle dager til sykmeldt når det ikke er beløp til arbeidsgiver`() {
        val dager =
            listOf(
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 5),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 6),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
            )

        val (daglisteSykmeldt, daglisteArbeidsgiver) = splittDaglister(dager)

        daglisteSykmeldt shouldBeEqualTo dager
        daglisteArbeidsgiver shouldBeEqualTo emptyList()
    }

    @Test
    fun `splittDaglister returnerer alle dager til arbeidsgiver når det ikke er beløp til sykmeldt`() {
        val dager =
            listOf(
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 5),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 6),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
            )

        val (daglisteSykmeldt, daglisteArbeidsgiver) = splittDaglister(dager)

        daglisteSykmeldt shouldBeEqualTo emptyList()
        daglisteArbeidsgiver shouldBeEqualTo dager
    }

    @Test
    fun `splittDaglister deler dager når begge har beløp og sykmeldt periode er tidligst`() {
        val dager =
            listOf(
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 5),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 6),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 7),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 8),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
            )

        val (daglisteSykmeldt, daglisteArbeidsgiver) = splittDaglister(dager)

        daglisteSykmeldt shouldBeEqualTo dager.slice(0..2)
        daglisteArbeidsgiver shouldBeEqualTo dager.slice(1..3)
    }

    @Test
    fun `splittDaglister legger dager før periode til riktig liste`() {
        val dager =
            listOf(
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 4),
                    type = "ArbeidsgiverperiodeDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 5),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 6),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
            )

        val (daglisteSykmeldt, daglisteArbeidsgiver) = splittDaglister(dager)

        daglisteSykmeldt shouldBeEqualTo dager.slice(2..2)
        daglisteArbeidsgiver shouldBeEqualTo dager.slice(0..1)
    }

    @Test
    fun `splittDaglister legger dager etter periode til riktig liste`() {
        val dager =
            listOf(
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 5),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 6),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 7),
                    type = "Helg",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 0,
                    begrunnelser = emptyList(),
                ),
            )

        val (daglisteSykmeldt, daglisteArbeidsgiver) = splittDaglister(dager)

        daglisteSykmeldt shouldBeEqualTo dager.slice(0..0)
        daglisteArbeidsgiver shouldBeEqualTo dager.slice(1..2)
    }

    @Test
    fun `splittDaglister legger dager før og etter til sykmeldt hvis fom og tom er lik`() {
        val dager =
            listOf(
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 4),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 5),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 6),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 7),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
            )

        val (daglisteSykmeldt, daglisteArbeidsgiver) = splittDaglister(dager)

        daglisteSykmeldt shouldBeEqualTo dager
        daglisteArbeidsgiver shouldBeEqualTo dager.slice(1..2)
    }

    @Test
    fun `splittDaglister legger dager før og etter i riktig sortert rekkefølge`() {
        val dager =
            listOf(
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 7),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 5),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 4),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
                RSUtbetalingdag(
                    dato = LocalDate.of(2024, 2, 6),
                    type = "NavDag",
                    beløpTilArbeidsgiver = 100,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                    begrunnelser = emptyList(),
                ),
            )

        val (daglisteSykmeldt, daglisteArbeidsgiver) = splittDaglister(dager)

        daglisteSykmeldt shouldBeEqualTo dager.sortedBy { it.dato }
        daglisteArbeidsgiver shouldBeEqualTo dager.sortedBy { it.dato }.slice(1..2)
    }
}
