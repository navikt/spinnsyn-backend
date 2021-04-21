package no.nav.helse.flex.domene

import no.nav.helse.flex.vedtak.domene.Periode
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodeTest {

    @Test
    fun `0101 0401 overlapper 0301 0501 og vice versa`() {

        val første = ("01.01" to "04.01").periode()
        val andre = ("03.01" to "05.01").periode()
        første.overlapper(andre) shouldBeEqualTo true
        andre.overlapper(første) shouldBeEqualTo true
    }

    @Test
    fun `0101 0101 og 0101 0101 overlapper`() {
        val første = ("01.01" to "01.01").periode()
        første.overlapper(første) shouldBeEqualTo true
    }

    @Test
    fun `0101 0101 og 0201 0201 overlapper ikke`() {
        val første = ("01.01" to "01.01").periode()
        val andre = ("02.01" to "02.01").periode()
        første.overlapper(andre) shouldBeEqualTo false
        andre.overlapper(første) shouldBeEqualTo false
    }

    @Test
    fun `0101 0201 og 0201 0301 overlapper`() {
        val første = ("01.01" to "02.01").periode()
        val andre = ("02.01" to "03.01").periode()
        første.overlapper(andre) shouldBeEqualTo true
        andre.overlapper(første) shouldBeEqualTo true
    }
}

fun Pair<String, String>.periode(): Periode {
    val fom = this.first.split(".").map { Integer.parseInt(it) }
    val tom = this.second.split(".").map { Integer.parseInt(it) }
    return Periode(LocalDate.of(2020, fom.last(), fom.first()), LocalDate.of(2020, tom.last(), tom.first()))
}
