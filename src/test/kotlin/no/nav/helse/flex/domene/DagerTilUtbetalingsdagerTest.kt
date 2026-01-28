package no.nav.helse.flex.domene

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DagerTilUtbetalingsdagerTest {
    @Test
    fun `kombinerer dager fra person og arbeidsgiver når begge eksisterer`() {
        val dagerPerson =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 1),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = listOf("Person begrunnelse"),
                ),
                RSDag(
                    dato = LocalDate.of(2025, 10, 2),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = emptyList(),
                ),
            )

        val dagerArbeidsgiver =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 1),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = listOf("Arbeidsgiver begrunnelse"),
                ),
                RSDag(
                    dato = LocalDate.of(2025, 10, 2),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = emptyList(),
                ),
            )

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(dagerPerson, dagerArbeidsgiver)

        utbetalingsdager.shouldHaveSize(2)

        utbetalingsdager[0].apply {
            dato shouldBeEqualTo LocalDate.of(2025, 10, 1)
            type shouldBeEqualTo "ArbeidsgiverperiodeDag"
            beløpTilSykmeldt shouldBeEqualTo 923
            beløpTilArbeidsgiver shouldBeEqualTo 923
            sykdomsgrad shouldBeEqualTo 100
            begrunnelser shouldBeEqualTo listOf("Person begrunnelse", "Arbeidsgiver begrunnelse")
        }

        utbetalingsdager[1].apply {
            dato shouldBeEqualTo LocalDate.of(2025, 10, 2)
            type shouldBeEqualTo "ArbeidsgiverperiodeDag"
            beløpTilSykmeldt shouldBeEqualTo 923
            beløpTilArbeidsgiver shouldBeEqualTo 923
            sykdomsgrad shouldBeEqualTo 100
            begrunnelser shouldBeEqualTo emptyList()
        }
    }

    @Test
    fun `håndterer kun person dager`() {
        val dagerPerson =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 28),
                    belop = 1846,
                    grad = 100.0,
                    dagtype = "NavDag",
                    begrunnelser = listOf("Kun person"),
                ),
            )

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(dagerPerson, emptyList())

        utbetalingsdager.shouldHaveSize(1)
        utbetalingsdager[0].apply {
            dato shouldBeEqualTo LocalDate.of(2025, 10, 28)
            type shouldBeEqualTo "NavDag"
            beløpTilSykmeldt shouldBeEqualTo 1846
            beløpTilArbeidsgiver shouldBeEqualTo null
            sykdomsgrad shouldBeEqualTo 100
            begrunnelser shouldBeEqualTo listOf("Kun person")
        }
    }

    @Test
    fun `håndterer kun arbeidsgiver dager`() {
        val dagerArbeidsgiver =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 1),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = listOf("Kun arbeidsgiver"),
                ),
            )

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(emptyList(), dagerArbeidsgiver)

        utbetalingsdager.shouldHaveSize(1)
        utbetalingsdager[0].apply {
            dato shouldBeEqualTo LocalDate.of(2025, 10, 1)
            type shouldBeEqualTo "ArbeidsgiverperiodeDag"
            beløpTilSykmeldt shouldBeEqualTo null
            beløpTilArbeidsgiver shouldBeEqualTo 923
            sykdomsgrad shouldBeEqualTo 100
            begrunnelser shouldBeEqualTo listOf("Kun arbeidsgiver")
        }
    }

    @Test
    fun `fyller ut manglende dager i perioden`() {
        val dagerPerson =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 1),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = emptyList(),
                ),
            )

        val dagerArbeidsgiver =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 3),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = emptyList(),
                ),
            )

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(dagerPerson, dagerArbeidsgiver)

        // Skal bare inneholde dagene som faktisk finnes, ikke fylle ut manglende
        utbetalingsdager.shouldHaveSize(2)
        utbetalingsdager[0].dato shouldBeEqualTo LocalDate.of(2025, 10, 1)
        utbetalingsdager[1].dato shouldBeEqualTo LocalDate.of(2025, 10, 3)
    }

    @Test
    fun `komplett eksempel basert på faktisk vedtak`() {
        val dagerPerson =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 1),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = emptyList(),
                ),
                RSDag(
                    dato = LocalDate.of(2025, 10, 2),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = emptyList(),
                ),
                RSDag(
                    dato = LocalDate.of(2025, 10, 28),
                    belop = 1846,
                    grad = 100.0,
                    dagtype = "NavDag",
                    begrunnelser = emptyList(),
                ),
                RSDag(
                    dato = LocalDate.of(2025, 10, 29),
                    belop = 1846,
                    grad = 100.0,
                    dagtype = "NavDag",
                    begrunnelser = emptyList(),
                ),
            )

        val dagerArbeidsgiver =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 1),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = emptyList(),
                ),
                RSDag(
                    dato = LocalDate.of(2025, 10, 2),
                    belop = 923,
                    grad = 100.0,
                    dagtype = "ArbeidsgiverperiodeDag",
                    begrunnelser = emptyList(),
                ),
            )

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(dagerPerson, dagerArbeidsgiver)

        utbetalingsdager.shouldHaveSize(4)

        // Dager med både person og arbeidsgiver
        utbetalingsdager[0].apply {
            dato shouldBeEqualTo LocalDate.of(2025, 10, 1)
            beløpTilSykmeldt shouldBeEqualTo 923
            beløpTilArbeidsgiver shouldBeEqualTo 923
        }

        utbetalingsdager[1].apply {
            dato shouldBeEqualTo LocalDate.of(2025, 10, 2)
            beløpTilSykmeldt shouldBeEqualTo 923
            beløpTilArbeidsgiver shouldBeEqualTo 923
        }

        // Dager med kun person
        utbetalingsdager[2].apply {
            dato shouldBeEqualTo LocalDate.of(2025, 10, 28)
            beløpTilSykmeldt shouldBeEqualTo 1846
            beløpTilArbeidsgiver shouldBeEqualTo null
        }

        utbetalingsdager[3].apply {
            dato shouldBeEqualTo LocalDate.of(2025, 10, 29)
            beløpTilSykmeldt shouldBeEqualTo 1846
            beløpTilArbeidsgiver shouldBeEqualTo null
        }
    }

    @Test
    fun `returnerer tom liste når ingen dager finnes`() {
        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(emptyList(), emptyList())
        utbetalingsdager.shouldHaveSize(0)
    }
}
