package no.nav.helse.flex.service

import io.kotest.matchers.collections.shouldContainExactly
import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.RSOppdrag
import no.nav.helse.flex.domene.RSUtbetalingdag
import no.nav.helse.flex.domene.RSUtbetalingslinje
import no.nav.helse.flex.util.hentDager
import no.nav.helse.flex.util.hentDagerNy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HentDagerTest {
    private val mandag = LocalDate.of(2023, 1, 2)

    @Test
    fun `Kun arbeidsgiverperiode`() {
        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(mandag.plusDays(0), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(1), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(2), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(3), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(4), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(5), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(6), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(7), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(8), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(9), "ArbeidsgiverperiodeDag", emptyList()),
            )

        val forventetArbeidgiverDagliste =
            listOf(
                RSDag(mandag.plusDays(0), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(1), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(2), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(3), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(4), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(5), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(6), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(7), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(8), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(9), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
            )

        hentDager(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(9),
            oppdragDto = null,
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(forventetArbeidgiverDagliste)

        hentDagerNy(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(9),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(forventetArbeidgiverDagliste)
    }

    @Test
    fun `Arbeidsgiverperiode oppbrukt`() {
        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(mandag.plusDays(0), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(1), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(2), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(3), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(4), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(5), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(6), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(7), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(8), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(9), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(10), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(11), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(12), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(13), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(14), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(15), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(16), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
                RSUtbetalingdag(mandag.plusDays(17), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
            )
        val forventetArbeidgiverDagliste =
            listOf(
                RSDag(mandag.plusDays(0), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(1), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(2), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(3), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(4), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(5), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(6), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(7), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(8), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(9), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(10), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(11), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(12), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(13), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(14), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(15), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(16), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(17), 100, 100.0, "NavDagSyk", emptyList()),
            )
        hentDager(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = mandag.plusDays(16),
                                tom = mandag.plusDays(17),
                                dagsats = 100,
                                totalbeløp = 200,
                                grad = 100.0,
                                stønadsdager = 2,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(
            forventetArbeidgiverDagliste,
        )

        hentDagerNy(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(
            forventetArbeidgiverDagliste,
        )
    }

    @Test
    fun `Arbeidsgiverperiode overtas av NAV midt i uka`() {
        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(mandag.plusDays(0), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(1), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(2), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(3), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(4), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(5), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(6), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(7), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(8), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(9), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(
                    mandag.plusDays(10),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(11),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(12),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(13),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(14),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(15),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(mandag.plusDays(16), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
                RSUtbetalingdag(mandag.plusDays(17), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
            )
        val forventetArbeidgiverDagliste =
            listOf(
                RSDag(mandag.plusDays(0), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(1), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(2), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(3), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(4), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(5), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(6), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(7), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(8), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(9), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(10), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(11), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(12), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(mandag.plusDays(13), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(mandag.plusDays(14), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(15), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(16), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(17), 100, 100.0, "NavDagSyk", emptyList()),
            )
        hentDager(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = mandag.plusDays(10),
                                tom = mandag.plusDays(17),
                                dagsats = 100,
                                totalbeløp = 800,
                                grad = 100.0,
                                stønadsdager = 8,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(
            forventetArbeidgiverDagliste,
        )
        hentDagerNy(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(forventetArbeidgiverDagliste)
    }

    @Test
    fun `Arbeidsgiverperiode overtas av NAV på mandag, da blir helgen før også overtatt og vises som helg`() {
        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(mandag.plusDays(0), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(1), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(2), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(3), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(4), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(5), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(mandag.plusDays(6), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(
                    mandag.plusDays(7),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(8),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(9),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(10),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(11),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(12),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(13),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(14),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    mandag.plusDays(15),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(mandag.plusDays(16), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
                RSUtbetalingdag(mandag.plusDays(17), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
            )
        val forventetArbeidgiverDagliste =
            listOf(
                RSDag(mandag.plusDays(0), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(1), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(2), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(3), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(4), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(mandag.plusDays(5), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(mandag.plusDays(6), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(mandag.plusDays(7), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(8), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(9), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(10), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(11), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(12), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(mandag.plusDays(13), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(mandag.plusDays(14), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(15), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(16), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(mandag.plusDays(17), 100, 100.0, "NavDagSyk", emptyList()),
            )
        hentDager(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = mandag.plusDays(7),
                                tom = mandag.plusDays(17),
                                dagsats = 100,
                                totalbeløp = 900,
                                grad = 100.0,
                                stønadsdager = 9,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(
            forventetArbeidgiverDagliste,
        )
        hentDagerNy(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(forventetArbeidgiverDagliste)
    }

    @Test
    fun `Arbeidsgiverperiode blir oppbrukt på en søndag`() {
        val søndag = mandag.minusDays(1)

        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(søndag.minusDays(15), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(14), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(13), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(12), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(11), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(10), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(9), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(8), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(7), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(6), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(5), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(4), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(3), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(2), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(1), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.minusDays(0), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(søndag.plusDays(1), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
                RSUtbetalingdag(søndag.plusDays(2), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
            )
        val forventetArbeidgiverDagliste =
            listOf(
                RSDag(søndag.minusDays(15), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(14), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(13), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(12), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(11), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(10), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(9), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(8), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(7), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(6), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(5), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(4), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(3), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(2), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(1), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.minusDays(0), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(1), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(2), 100, 100.0, "NavDagSyk", emptyList()),
            )
        hentDager(
            fom = søndag.minusDays(15),
            tom = søndag.plusDays(2),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = søndag.plusDays(1),
                                tom = søndag.plusDays(2),
                                dagsats = 100,
                                totalbeløp = 200,
                                grad = 100.0,
                                stønadsdager = 2,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(
            forventetArbeidgiverDagliste,
        )
        hentDagerNy(
            fom = søndag.minusDays(15),
            tom = søndag.plusDays(2),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(forventetArbeidgiverDagliste)
    }

    @Test
    fun `Arbeidsgiverperiode overtas av NAV og første dag er i helga`() {
        val søndag = mandag.minusDays(1)

        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(søndag.plusDays(0), "ArbeidsgiverperiodeDag", emptyList()),
                RSUtbetalingdag(
                    søndag.plusDays(1),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(2),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(3),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(4),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(5),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(6),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(7),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(8),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(9),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(10),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(11),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(12),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(13),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(14),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(15),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(søndag.plusDays(16), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
                RSUtbetalingdag(søndag.plusDays(17), "NavDag", emptyList(), beløpTilSykmeldt = 100, sykdomsgrad = 100),
            )
        val forventetArbeidgiverDagliste =
            listOf(
                RSDag(søndag.plusDays(0), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(1), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(2), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(3), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(4), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(5), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(6), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(7), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(8), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(9), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(10), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(11), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(12), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(13), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(14), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(15), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(16), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(17), 100, 100.0, "NavDagSyk", emptyList()),
            )
        hentDager(
            fom = søndag.plusDays(0),
            tom = søndag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = søndag.plusDays(1),
                                tom = søndag.plusDays(17),
                                dagsats = 100,
                                totalbeløp = 1300,
                                grad = 100.0,
                                stønadsdager = 13,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(
            forventetArbeidgiverDagliste,
        )
        hentDagerNy(
            fom = søndag.plusDays(0),
            tom = søndag.plusDays(17),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(forventetArbeidgiverDagliste)
    }

    @Test
    fun `Både brukerutbetaling og arbeidsgiverrefusjon i arbeidsgiverperiode`() {
        val søndag = mandag.minusDays(1)

        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(
                    søndag.plusDays(0),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 200,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(1),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 200,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(2),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 200,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(3),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 200,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(4),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(5),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(6),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(7),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(8),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(9),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(10),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(11),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(12),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(13),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(14),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(15),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(16),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    søndag.plusDays(17),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 100,
                    sykdomsgrad = 100,
                ),
            )
        val forventetArbeidsgiverDagliste =
            listOf(
                RSDag(søndag.plusDays(0), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(1), 200, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(2), 200, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(3), 200, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(4), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(5), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(6), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(7), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(8), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(9), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(10), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(11), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(12), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(13), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(14), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(15), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
            )

        val forventetSykmeldtDagliste =
            listOf(
                RSDag(søndag.plusDays(0), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(1), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(2), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(3), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(søndag.plusDays(4), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(5), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(6), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(7), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(8), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(9), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(10), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(11), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(12), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(13), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(14), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(søndag.plusDays(15), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(16), 100, 100.0, "NavDagSyk", emptyList()),
                RSDag(søndag.plusDays(17), 100, 100.0, "NavDagSyk", emptyList()),
            )

        hentDager(
            fom = søndag.plusDays(0),
            tom = søndag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = søndag.plusDays(0),
                                tom = søndag.plusDays(3),
                                dagsats = 200,
                                totalbeløp = 600,
                                grad = 100.0,
                                stønadsdager = 4,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(forventetArbeidsgiverDagliste)

        hentDagerNy(
            fom = søndag.plusDays(0),
            tom = søndag.plusDays(17),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = false,
        ).shouldContainExactly(forventetArbeidsgiverDagliste)

        hentDager(
            fom = søndag.plusDays(0),
            tom = søndag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = søndag.plusDays(4),
                                tom = søndag.plusDays(17),
                                dagsats = 100,
                                totalbeløp = 1000,
                                grad = 100.0,
                                stønadsdager = 14,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(forventetSykmeldtDagliste)

        hentDagerNy(
            fom = søndag.plusDays(0),
            tom = søndag.plusDays(17),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(forventetSykmeldtDagliste)
    }

    @Test
    fun `Både brukerutbetaling og arbeidsgiverrefusjon med mange utbetalingsperioder og ferie`() {
        val dato = LocalDate.of(2025, 11, 1)

        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(
                    dato.plusDays(0),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(1),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(2),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 2308,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(3),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(4),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(5),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(6),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(7),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(8),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(9),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(10),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 0,
                ),
                RSUtbetalingdag(
                    dato.plusDays(11),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 0,
                ),
                RSUtbetalingdag(
                    dato.plusDays(12),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(13),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(14),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(15),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(16),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(17),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(18),
                    "Feriedag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 0,
                ),
                RSUtbetalingdag(
                    dato.plusDays(19),
                    "Feriedag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 0,
                ),
                RSUtbetalingdag(
                    dato.plusDays(20),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(21),
                    "NavHelgDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(22),
                    "NavHelgDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(23),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(24),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(25),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(26),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(27),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 2308,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(28),
                    "NavHelgDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(29),
                    "NavHelgDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
            )

        val forventetArbeidsgiverDagliste =
            listOf(
                RSDag(dato.plusDays(0), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(1), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(2), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(3), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(4), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(5), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(6), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(7), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(8), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(9), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(10), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(11), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(12), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(13), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(14), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(15), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
            )

        val forventetSykmeldtDagliste =
            listOf(
                RSDag(dato.plusDays(0), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(1), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(2), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(3), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(4), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(5), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(6), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(7), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(8), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(9), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(10), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(11), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(12), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(13), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(14), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(15), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(16), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(17), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(18), 0, 0.0, "Feriedag", emptyList()),
                RSDag(dato.plusDays(19), 0, 0.0, "Feriedag", emptyList()),
                RSDag(dato.plusDays(20), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(21), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(22), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(23), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(24), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(25), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(26), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(27), 2308, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(28), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(29), 0, 0.0, "NavHelgDag", emptyList()),
            )

        hentDager(
            fom = dato.plusDays(0),
            tom = dato.plusDays(29),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = dato.plusDays(2),
                                tom = dato.plusDays(2),
                                dagsats = 2308,
                                totalbeløp = 2308,
                                grad = 100.0,
                                stønadsdager = 1,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(forventetArbeidsgiverDagliste)

        hentDagerNy(
            fom = dato.plusDays(0),
            tom = dato.plusDays(29),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = false,
        ).shouldContainExactly(forventetArbeidsgiverDagliste)

        hentDager(
            fom = dato.plusDays(0),
            tom = dato.plusDays(29),
            oppdragDto =
                RSOppdrag(
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = dato.plusDays(3),
                                tom = dato.plusDays(9),
                                dagsats = 2308,
                                totalbeløp = 11540,
                                grad = 100.0,
                                stønadsdager = 5,
                            ),
                            RSUtbetalingslinje(
                                fom = dato.plusDays(12),
                                tom = dato.plusDays(17),
                                dagsats = 2308,
                                totalbeløp = 9232,
                                grad = 100.0,
                                stønadsdager = 4,
                            ),
                            RSUtbetalingslinje(
                                fom = dato.plusDays(20),
                                tom = dato.plusDays(29),
                                dagsats = 2308,
                                totalbeløp = 13848,
                                grad = 100.0,
                                stønadsdager = 6,
                            ),
                        ),
                ),
            utbetalingsdager = utbetalingsdager,
        ).shouldContainExactly(forventetSykmeldtDagliste)

        hentDagerNy(
            fom = dato.plusDays(0),
            tom = dato.plusDays(29),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(forventetSykmeldtDagliste)
    }

    @Test
    fun `NavDagDelvisSyk basert på at dagen er nullet ut`() {
        val dato = LocalDate.of(2025, 11, 8)

        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(
                    dato.plusDays(0),
                    "NavHelgDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(1),
                    "NavHelgDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(2),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(3),
                    "NavDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(4),
                    "AvvistDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(5),
                    "AvvistDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(6),
                    "AvvistDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(7),
                    "NavHelgDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(8),
                    "NavHelgDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
            )

        val forventetSykmeldtDagliste =
            listOf(
                RSDag(dato.plusDays(0), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(1), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(2), 0, 0.0, "NavDagDelvisSyk", emptyList()),
                RSDag(dato.plusDays(3), 0, 0.0, "NavDagDelvisSyk", emptyList()),
                RSDag(dato.plusDays(4), 0, 0.0, "AvvistDag", emptyList()),
                RSDag(dato.plusDays(5), 0, 0.0, "AvvistDag", emptyList()),
                RSDag(dato.plusDays(6), 0, 0.0, "AvvistDag", emptyList()),
                RSDag(dato.plusDays(7), 0, 0.0, "NavHelgDag", emptyList()),
                RSDag(dato.plusDays(8), 0, 0.0, "NavHelgDag", emptyList()),
            )

        val hentDager =
            hentDager(
                fom = dato.plusDays(0),
                tom = dato.plusDays(8),
                oppdragDto =
                    RSOppdrag(
                        utbetalingslinjer = listOf(),
                    ),
                utbetalingsdager = utbetalingsdager,
            )
        hentDager.shouldContainExactly(forventetSykmeldtDagliste)

        hentDagerNy(
            fom = dato.plusDays(0),
            tom = dato.plusDays(8),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = true,
        ).shouldContainExactly(forventetSykmeldtDagliste)
    }

    @Test
    fun `Helg på slutten av arbeidsgiverperiode`() {
        val dato = LocalDate.of(2025, 12, 4)

        val utbetalingsdager =
            listOf(
                RSUtbetalingdag(
                    dato.plusDays(0),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 2018,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(1),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 2018,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(2),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
                RSUtbetalingdag(
                    dato.plusDays(3),
                    "ArbeidsgiverperiodeDag",
                    emptyList(),
                    beløpTilArbeidsgiver = 0,
                    beløpTilSykmeldt = 0,
                    sykdomsgrad = 100,
                ),
            )

        val forventetArbeidsgiverDagliste =
            listOf(
                RSDag(dato.plusDays(0), 2018, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(1), 2018, 100.0, "NavDagSyk", emptyList()),
                RSDag(dato.plusDays(2), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
                RSDag(dato.plusDays(3), 0, 0.0, "ArbeidsgiverperiodeDag", emptyList()),
            )

        hentDagerNy(
            fom = dato.plusDays(0),
            tom = dato.plusDays(3),
            utbetalingsdager = utbetalingsdager,
            erSykmeldt = false,
        ).shouldContainExactly(forventetArbeidsgiverDagliste)
    }
}
