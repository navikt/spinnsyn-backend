package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.service.SendVedtakStatus.Companion.sjekkDager
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VedtakSkalVisesTest {
    private fun dagSequence(dagtype: String) =
        generateSequence(
            RSDag(
                dato = LocalDate.of(2022, 1, 3),
                belop = 0,
                grad = 100.0,
                dagtype = dagtype,
                begrunnelser = emptyList(),
            ),
        ) {
            it.copy(dato = it.dato.plusDays(1))
        }

    @Test
    fun `Vedtak med bare arbeidsgiverperiode dager skal ikke vises`() {
        val dager = dagSequence("ArbeidsgiverperiodeDag").take(10).toList()
        sjekkDager(dager) shouldBeEqualTo "ArbeidsgiverperiodeDag"
    }

    @Test
    fun `Vedtak med arbeidsgiverperiode og arbeidsdager skal ikke vises der siste dag er arbeidsgiverperiode`() {
        val dager =
            dagSequence("ArbeidsgiverperiodeDag")
                .take(10)
                .mapIndexed { idx, dag ->
                    if (idx in 2..5) {
                        dag.copy(dagtype = "Arbeidsdag")
                    } else {
                        dag
                    }
                }.toList()

        sjekkDager(dager) shouldBeEqualTo "ArbeidsgiverperiodeMedArbeid"
    }

    @Test
    fun `Vedtak med arbeidsgiverperiode og arbeidsdager på slutten skal vises til bruker`() {
        val dager =
            dagSequence("ArbeidsgiverperiodeDag")
                .take(10)
                .plus(
                    dagSequence("Arbeidsdag").take(1),
                ).toList()

        sjekkDager(dager) shouldBeEqualTo ""
    }
}
