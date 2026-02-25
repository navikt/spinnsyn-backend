package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.RSVedtakWrapper
import java.time.LocalDate

fun RSVedtakWrapper.fjernArbeidIkkeGjenopptattDager(): RSVedtakWrapper {
    val daglisteSykmeldt = this.daglisteSykmeldt.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }
    val daglisteArbeidsgiver = this.daglisteArbeidsgiver.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }

    val faktiskFomNy = finnFaktiskFom(daglisteArbeidsgiver, daglisteSykmeldt, this.vedtak.fom)

    return this.copy(
        daglisteArbeidsgiver = daglisteArbeidsgiver,
        daglisteSykmeldt = daglisteSykmeldt,
        vedtak = this.vedtak.copy(fom = faktiskFomNy),
    )
}

fun finnFaktiskFom(
    dagerArbeidsgiver: List<RSDag>,
    dagerSykmeldt: List<RSDag>,
    fom: LocalDate,
): LocalDate {
    val tidligsteDagerDag = (dagerArbeidsgiver + dagerSykmeldt).minByOrNull { it.dato }
    if (tidligsteDagerDag != null) {
        if (tidligsteDagerDag.dato.isAfter(fom)) {
            return tidligsteDagerDag.dato
        }
    }
    return fom
}
