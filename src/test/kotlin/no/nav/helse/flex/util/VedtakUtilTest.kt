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
        val korrigert = korrigerUtbetalingsdager(listOf(dag)).first()
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
        val korrigert = korrigerUtbetalingsdager(listOf(dag)).first()
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
        val korrigert = korrigerUtbetalingsdager(listOf(dag)).first()
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
        val korrigert = korrigerUtbetalingsdager(listOf(dag)).first()
        korrigert.type shouldBeEqualTo "NavHelgDag"
        korrigert.beløpTilArbeidsgiver shouldBeEqualTo 0
        korrigert.beløpTilSykmeldt shouldBeEqualTo 0
        korrigert.sykdomsgrad shouldBeEqualTo 0
    }

    @Test
    fun `NavDag på helg blir NavHelgDag`() {
        val sondag = LocalDate.of(2024, 2, 11)
        val dag =
            RSUtbetalingdag(
                dato = sondag,
                type = "NavDag",
                beløpTilArbeidsgiver = 100,
                beløpTilSykmeldt = 100,
                sykdomsgrad = 100,
                begrunnelser = emptyList(),
            )
        val korrigert = korrigerUtbetalingsdager(listOf(dag)).first()
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
        val korrigert = korrigerUtbetalingsdager(listOf(dag)).first()
        korrigert.type shouldBeEqualTo "NavDag"
        korrigert.beløpTilArbeidsgiver shouldBeEqualTo 100
        korrigert.beløpTilSykmeldt shouldBeEqualTo 100
        korrigert.sykdomsgrad shouldBeEqualTo 100
    }
}
