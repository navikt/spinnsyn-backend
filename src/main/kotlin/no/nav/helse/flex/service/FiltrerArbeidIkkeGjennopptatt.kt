package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSVedtakWrapper
import java.time.LocalDate

fun RSVedtakWrapper.fjernArbeidIkkeGjenopptattDager(): RSVedtakWrapper {
    val dagerArbeidsgiver = this.dagerArbeidsgiver.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }
    val dagerPerson = this.dagerPerson.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }

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
