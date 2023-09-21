package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.RSVedtakWrapper
import java.time.LocalDate

fun RSVedtakWrapper.fjernArbeidIkkeGjenopptattDager(): RSVedtakWrapper {
    val dagerArbeidsgiver = this.dagerArbeidsgiver.filtrerArbeidIkkeGjenopptattDager()
    val dagerPerson = this.dagerPerson.filtrerArbeidIkkeGjenopptattDager()

    fun finnFaktiskFom(): LocalDate {
        val tidligsteDagerDag = (dagerArbeidsgiver + dagerPerson).minByOrNull { it.dato }
        if (tidligsteDagerDag != null) {
            if (tidligsteDagerDag.dato.isAfter(this.vedtak.fom)) {
                return tidligsteDagerDag.dato
            }
        }
        return this.vedtak.fom
    }

    return this.copy(
        dagerArbeidsgiver = dagerArbeidsgiver,
        dagerPerson = dagerPerson,
        vedtak = this.vedtak.copy(fom = finnFaktiskFom())
    )
}

private fun List<RSDag>.filtrerArbeidIkkeGjenopptattDager(): List<RSDag> {
    var funnetVanligDag = false
    return this.sortedBy { it.dato }.filter {
        if (funnetVanligDag) return@filter true
        if (it.dagtype == "ArbeidIkkeGjenopptattDag") {
            return@filter false
        } else {
            funnetVanligDag = true
            return@filter true
        }
    }
}
