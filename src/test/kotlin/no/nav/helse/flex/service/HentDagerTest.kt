package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.RSOppdrag
import no.nav.helse.flex.domene.RSUtbetalingdag
import no.nav.helse.flex.domene.RSUtbetalingslinje
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HentDagerTest {
    private val mandag = LocalDate.of(2023, 1, 2)

    @Test
    fun `Kun arbeidsgiverperiode`() {
        hentDager(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(9),
            oppdragDto = null,
            utbetalingsdager =
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
                ),
        ).shouldBeEqualTo(
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
            ),
        )
    }

    @Test
    fun `Arbeidsgiverperiode oppbrukt`() {
        hentDager(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    mottaker = "12345678",
                    nettoBeløp = 200,
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = mandag.plusDays(16),
                                tom = mandag.plusDays(17),
                                dagsats = 100,
                                dagsatsTransformasjonHjelper = 100,
                                totalbeløp = 200,
                                grad = 100.0,
                                stønadsdager = 2,
                            ),
                        ),
                ),
            utbetalingsdager =
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
                    RSUtbetalingdag(mandag.plusDays(16), "NavDag", emptyList()),
                    RSUtbetalingdag(mandag.plusDays(17), "NavDag", emptyList()),
                ),
        ).shouldBeEqualTo(
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
            ),
        )
    }

    @Test
    fun `Arbeidsgiverperiode overtas av NAV midt i uka`() {
        hentDager(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    mottaker = "12345678",
                    nettoBeløp = 800,
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = mandag.plusDays(10),
                                tom = mandag.plusDays(17),
                                dagsats = 100,
                                dagsatsTransformasjonHjelper = 100,
                                totalbeløp = 800,
                                grad = 100.0,
                                stønadsdager = 8,
                            ),
                        ),
                ),
            utbetalingsdager =
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
                    RSUtbetalingdag(mandag.plusDays(16), "NavDag", emptyList()),
                    RSUtbetalingdag(mandag.plusDays(17), "NavDag", emptyList()),
                ),
        ).shouldBeEqualTo(
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
            ),
        )
    }

    @Test
    fun `Arbeidsgiverperiode overtas av NAV på mandag, da blir helgen før også overtatt og vises som helg`() {
        hentDager(
            fom = mandag.plusDays(0),
            tom = mandag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    mottaker = "12345678",
                    nettoBeløp = 900,
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = mandag.plusDays(7),
                                tom = mandag.plusDays(17),
                                dagsats = 100,
                                dagsatsTransformasjonHjelper = 100,
                                totalbeløp = 900,
                                grad = 100.0,
                                stønadsdager = 9,
                            ),
                        ),
                ),
            utbetalingsdager =
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
                    RSUtbetalingdag(mandag.plusDays(16), "NavDag", emptyList()),
                    RSUtbetalingdag(mandag.plusDays(17), "NavDag", emptyList()),
                ),
        ).shouldBeEqualTo(
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
            ),
        )
    }

    @Test
    fun `Arbeidsgiverperiode blir oppbrukt på en søndag`() {
        val søndag = mandag.minusDays(1)

        hentDager(
            fom = søndag.minusDays(15),
            tom = søndag.plusDays(2),
            oppdragDto =
                RSOppdrag(
                    mottaker = "12345678",
                    nettoBeløp = 200,
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = søndag.plusDays(1),
                                tom = søndag.plusDays(2),
                                dagsats = 100,
                                dagsatsTransformasjonHjelper = 100,
                                totalbeløp = 200,
                                grad = 100.0,
                                stønadsdager = 2,
                            ),
                        ),
                ),
            utbetalingsdager =
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
                    RSUtbetalingdag(søndag.plusDays(1), "NavDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(2), "NavDag", emptyList()),
                ),
        ).shouldBeEqualTo(
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
            ),
        )
    }

    @Test
    fun `Arbeidsgiverperiode overtas av NAV og første dag er i helga`() {
        val søndag = mandag.minusDays(1)

        hentDager(
            fom = søndag.plusDays(0),
            tom = søndag.plusDays(17),
            oppdragDto =
                RSOppdrag(
                    mottaker = "12345678",
                    nettoBeløp = 1300,
                    utbetalingslinjer =
                        listOf(
                            RSUtbetalingslinje(
                                fom = søndag.plusDays(1),
                                tom = søndag.plusDays(17),
                                dagsats = 100,
                                dagsatsTransformasjonHjelper = 100,
                                totalbeløp = 1300,
                                grad = 100.0,
                                stønadsdager = 13,
                            ),
                        ),
                ),
            utbetalingsdager =
                listOf(
                    RSUtbetalingdag(søndag.plusDays(0), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(1), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(2), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(3), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(4), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(5), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(6), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(7), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(8), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(9), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(10), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(11), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(12), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(13), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(14), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(15), "ArbeidsgiverperiodeDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(16), "NavDag", emptyList()),
                    RSUtbetalingdag(søndag.plusDays(17), "NavDag", emptyList()),
                ),
        ).shouldBeEqualTo(
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
            ),
        )
    }
}
