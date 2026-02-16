package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.RSUtbetalingdag
import no.nav.helse.flex.vedtakTestdata
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FiltrerArbeidIkkeGjennopptattKtTest {
    private val dato: LocalDate = LocalDate.parse("2021-01-01")

    @Test
    fun `fjerner arbeid ikke gjenopptatt dager`() {
        val utbetalingsdager =
            listOf(
                rsUtbetalingdag(5, "ArbeidIkkeGjenopptattDag"),
                rsUtbetalingdag(4, "ArbeidIkkeGjenopptattDag"),
                rsUtbetalingdag(3, "NavDag"),
            )
        val vedtakMedDager =
            vedtakTestdata().copy(
                dagerPerson =
                    listOf(
                        rSDag(5, "ArbeidIkkeGjenopptattDag"),
                        rSDag(4, "ArbeidIkkeGjenopptattDag"),
                        rSDag(3, "NavDag"),
                    ),
                vedtak =
                    vedtakTestdata().vedtak.copy(
                        fom = dato.minusDays(5),
                        utbetaling = vedtakTestdata().vedtak.utbetaling.copy(utbetalingsdager = utbetalingsdager),
                    ),
            )

        val vedtakUtenArbeidIkkeGjenopptattDager = vedtakMedDager.fjernArbeidIkkeGjenopptattDager()
        vedtakUtenArbeidIkkeGjenopptattDager.dagerPerson.shouldHaveSize(1)
        vedtakUtenArbeidIkkeGjenopptattDager.dagerPerson.all { it.dagtype == "NavDag" } shouldBeEqualTo true
        vedtakUtenArbeidIkkeGjenopptattDager.vedtak.fom shouldBeEqualTo dato.minusDays(3)
        vedtakUtenArbeidIkkeGjenopptattDager.vedtak.utbetaling.utbetalingsdager
            ?.shouldHaveSize(1)
        vedtakUtenArbeidIkkeGjenopptattDager.vedtak.utbetaling.utbetalingsdager
            ?.all { it.type == "NavDag" } shouldBeEqualTo true
    }

    @Test
    fun `fjerner ikke vanlige dager`() {
        val utbetalingsdager =
            listOf(
                rsUtbetalingdag(5, "NavDag"),
                rsUtbetalingdag(4, "NavDag"),
                rsUtbetalingdag(3, "NavDag"),
            )
        val vedtakMedDager =
            vedtakTestdata().copy(
                dagerPerson =
                    listOf(
                        rSDag(5, "NavDag"),
                        rSDag(4, "NavDag"),
                        rSDag(3, "NavDag"),
                    ),
                vedtak =
                    vedtakTestdata().vedtak.copy(
                        fom = dato.minusDays(5),
                        utbetaling = vedtakTestdata().vedtak.utbetaling.copy(utbetalingsdager = utbetalingsdager),
                    ),
            )

        val vedtakUtenArbeidIkkeGjenopptattDager = vedtakMedDager.fjernArbeidIkkeGjenopptattDager()
        vedtakUtenArbeidIkkeGjenopptattDager.dagerPerson.shouldHaveSize(3)
        vedtakUtenArbeidIkkeGjenopptattDager.vedtak.fom shouldBeEqualTo dato.minusDays(5)
    }

    @Test
    fun `fjerner arbeid ikke gjenopptatt utbetalingsdager og dagerPerson og setter fom riktig`() {
        val dagerPerson =
            listOf(
                rSDag(5, "ArbeidIkkeGjenopptattDag"),
                rSDag(4, "ArbeidIkkeGjenopptattDag"),
                rSDag(3, "NavDag"),
            )
        val utbetalingsdager =
            listOf(
                rsUtbetalingdag(5, "ArbeidIkkeGjenopptattDag"),
                rsUtbetalingdag(4, "ArbeidIkkeGjenopptattDag"),
                rsUtbetalingdag(3, "NavDag"),
            )

        val vedtakMedBegge =
            vedtakTestdata().copy(
                dagerPerson = dagerPerson,
                dagerArbeidsgiver = emptyList(),
                vedtak =
                    vedtakTestdata().vedtak.copy(
                        fom = dato.minusDays(5),
                        utbetaling =
                            vedtakTestdata().vedtak.utbetaling.copy(
                                utbetalingsdager = utbetalingsdager,
                            ),
                    ),
            )

        val resultat = vedtakMedBegge.fjernArbeidIkkeGjenopptattDager()
        resultat.vedtak.utbetaling.utbetalingsdager
            ?.shouldHaveSize(1)
        resultat.vedtak.utbetaling.utbetalingsdager
            ?.all { it.type == "NavDag" } shouldBeEqualTo true
        resultat.dagerPerson.shouldHaveSize(1)
        resultat.dagerPerson.all { it.dagtype == "NavDag" } shouldBeEqualTo true
        resultat.vedtak.fom shouldBeEqualTo dato.minusDays(3)
    }

    fun rSDag(
        dagersiden: Int,
        dagtype: String,
    ): RSDag =
        RSDag(
            dato = dato.minusDays(dagersiden.toLong()),
            dagtype = dagtype,
            begrunnelser = emptyList(),
            belop = 12,
            grad = 100.0,
        )

    fun rsUtbetalingdag(
        dagersiden: Int,
        type: String,
    ) = RSUtbetalingdag(
        dato = dato.minusDays(dagersiden.toLong()),
        type = type,
        begrunnelser = emptyList(),
        beløpTilArbeidsgiver = 12,
        sykdomsgrad = 100,
    )
}
