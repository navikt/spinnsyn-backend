package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.vedtakTestdata
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FiltrerArbeidIkkeGjennopptattKtTest {
    private val dato: LocalDate = LocalDate.parse("2021-01-01")

    @Test
    fun `fjerner arbeid ikke gjenopptatt dager`() {
        val vedtakMedDager =
            vedtakTestdata().copy(
                dagerPerson =
                    listOf(
                        rSDag(5, "ArbeidIkkeGjenopptattDag"),
                        rSDag(4, "ArbeidIkkeGjenopptattDag"),
                        rSDag(3, "NAVDag"),
                    ),
                vedtak = vedtakTestdata().vedtak.copy(fom = dato.minusDays(5)),
            )

        val vedtakUtenArbeidIkkeGjenopptattDager = vedtakMedDager.fjernArbeidIkkeGjenopptattDager()
        vedtakUtenArbeidIkkeGjenopptattDager.dagerPerson.shouldHaveSize(1)
        vedtakUtenArbeidIkkeGjenopptattDager.vedtak.fom shouldBeEqualTo (dato.minusDays(3))
    }

    @Test
    fun `fjerner ikke vanlige dager`() {
        val vedtakMedDager =
            vedtakTestdata().copy(
                dagerPerson =
                    listOf(
                        rSDag(5, "NAVDag"),
                        rSDag(4, "NAVDag"),
                        rSDag(3, "NAVDag"),
                    ),
                vedtak = vedtakTestdata().vedtak.copy(fom = dato.minusDays(5)),
            )

        val vedtakUtenArbeidIkkeGjenopptattDager = vedtakMedDager.fjernArbeidIkkeGjenopptattDager()
        vedtakUtenArbeidIkkeGjenopptattDager.dagerPerson.shouldHaveSize(3)
        vedtakUtenArbeidIkkeGjenopptattDager.vedtak.fom shouldBeEqualTo dato.minusDays(5)
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
}
