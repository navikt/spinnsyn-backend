package no.nav.helse.flex.domene
/*

import no.nav.helse.flex.vedtak.domene.Periode
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object PeriodeSpek : Spek({
    describe("Tester overlapp") {
        it("(01.01, 04.01) overlapper (03.01, 05.01) og vice versa") {
            val første = ("01.01" to "04.01").periode()
            val andre = ("03.01" to "05.01").periode()
            første.overlapper(andre) shouldEqual true
            andre.overlapper(første) shouldEqual true
        }

        it("(01.01, 01.01) og (01.01, 01.01) overlapper") {
            val første = ("01.01" to "01.01").periode()
            første.overlapper(første) shouldEqual true
        }

        it("(01.01, 01.01) og (02.01, 02.01) overlapper ikke") {
            val første = ("01.01" to "01.01").periode()
            val andre = ("02.01" to "02.01").periode()
            første.overlapper(andre) shouldEqual false
            andre.overlapper(første) shouldEqual false
        }
        it("(01.01, 02.01) og (02.01, 03.01) overlapper") {
            val første = ("01.01" to "02.01").periode()
            val andre = ("02.01" to "03.01").periode()
            første.overlapper(andre) shouldEqual true
            andre.overlapper(første) shouldEqual true
        }
    }
})

fun Pair<String, String>.periode(): Periode {
    val fom = this.first.split(".").map { Integer.parseInt(it) }
    val tom = this.second.split(".").map { Integer.parseInt(it) }
    return Periode(LocalDate.of(2020, fom.last(), fom.first()), LocalDate.of(2020, tom.last(), tom.first()))
}
*/
