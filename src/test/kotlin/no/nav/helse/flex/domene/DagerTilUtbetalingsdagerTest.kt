package no.nav.helse.flex.domene

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DagerTilUtbetalingsdagerTest {
    @Test
    fun `kombinerer dager fra sykmeldt og arbeidsgiver når begge eksisterer`() {
        val dagerSykmeldt =
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

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(dagerSykmeldt, dagerArbeidsgiver)

        utbetalingsdager.shouldHaveSize(2)

        utbetalingsdager[0].apply {
            dato shouldBe LocalDate.of(2025, 10, 1)
            type shouldBe "ArbeidsgiverperiodeDag"
            beløpTilSykmeldt shouldBe 923
            beløpTilArbeidsgiver shouldBe 923
            sykdomsgrad shouldBe 100
            begrunnelser shouldBe listOf("Person begrunnelse", "Arbeidsgiver begrunnelse")
        }

        utbetalingsdager[1].apply {
            dato shouldBe LocalDate.of(2025, 10, 2)
            type shouldBe "ArbeidsgiverperiodeDag"
            beløpTilSykmeldt shouldBe 923
            beløpTilArbeidsgiver shouldBe 923
            sykdomsgrad shouldBe 100
            begrunnelser shouldBe emptyList()
        }
    }

    @Test
    fun `håndterer kun sykmeldt dager`() {
        val dagerSykmeldt =
            listOf(
                RSDag(
                    dato = LocalDate.of(2025, 10, 28),
                    belop = 1846,
                    grad = 100.0,
                    dagtype = "NavDag",
                    begrunnelser = listOf("Kun person"),
                ),
            )

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(dagerSykmeldt, emptyList())

        utbetalingsdager.shouldHaveSize(1)
        utbetalingsdager[0].apply {
            dato shouldBe LocalDate.of(2025, 10, 28)
            type shouldBe "NavDag"
            beløpTilSykmeldt shouldBe 1846
            beløpTilArbeidsgiver shouldBe 0
            sykdomsgrad shouldBe 100
            begrunnelser shouldBe listOf("Kun person")
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
            dato shouldBe LocalDate.of(2025, 10, 1)
            type shouldBe "ArbeidsgiverperiodeDag"
            beløpTilSykmeldt shouldBe 0
            beløpTilArbeidsgiver shouldBe 923
            sykdomsgrad shouldBe 100
            begrunnelser shouldBe listOf("Kun arbeidsgiver")
        }
    }

    @Test
    fun `fyller ut manglende dager i perioden`() {
        val dagerSykmeldt =
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

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(dagerSykmeldt, dagerArbeidsgiver)

        // Skal bare inneholde dagene som faktisk finnes, ikke fylle ut manglende
        utbetalingsdager.shouldHaveSize(2)
        utbetalingsdager[0].dato shouldBe LocalDate.of(2025, 10, 1)
        utbetalingsdager[1].dato shouldBe LocalDate.of(2025, 10, 3)
    }

    @Test
    fun `komplett eksempel basert på faktisk vedtak`() {
        val dagerSykmeldt =
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

        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(dagerSykmeldt, dagerArbeidsgiver)

        utbetalingsdager.shouldHaveSize(4)

        // Dager med både person og arbeidsgiver
        utbetalingsdager[0].apply {
            dato shouldBe LocalDate.of(2025, 10, 1)
            beløpTilSykmeldt shouldBe 923
            beløpTilArbeidsgiver shouldBe 923
        }

        utbetalingsdager[1].apply {
            dato shouldBe LocalDate.of(2025, 10, 2)
            beløpTilSykmeldt shouldBe 923
            beløpTilArbeidsgiver shouldBe 923
        }

        // Dager med kun person
        utbetalingsdager[2].apply {
            dato shouldBe LocalDate.of(2025, 10, 28)
            beløpTilSykmeldt shouldBe 1846
            beløpTilArbeidsgiver shouldBe 0
        }

        utbetalingsdager[3].apply {
            dato shouldBe LocalDate.of(2025, 10, 29)
            beløpTilSykmeldt shouldBe 1846
            beløpTilArbeidsgiver shouldBe 0
        }
    }

    @Test
    fun `returnerer tom liste når ingen dager finnes`() {
        val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(emptyList(), emptyList())
        utbetalingsdager.shouldHaveSize(0)
    }
}
