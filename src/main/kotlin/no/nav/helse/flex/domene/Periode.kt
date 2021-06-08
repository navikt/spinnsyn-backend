package no.nav.helse.flex.domene

import java.time.LocalDate

interface Periode {

    val fom: LocalDate
    val tom: LocalDate

    fun overlapper(andre: Periode) =
        (this.fom >= andre.fom && this.fom <= andre.tom) ||
            (this.tom <= andre.tom && this.tom >= andre.fom)
}

class PeriodeImpl(override val fom: LocalDate, override val tom: LocalDate) : Periode
