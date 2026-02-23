package no.nav.helse.flex.service

import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.logger
import java.time.LocalDate

fun RSVedtakWrapper.fjernArbeidIkkeGjenopptattDager(): RSVedtakWrapper {
    val dagerArbeidsgiver = this.dagerArbeidsgiver.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }
    val dagerPerson = this.dagerPerson.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }

    val daglisteSykmeldt = this.daglisteSykmeldt.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }
    val daglisteArbeidsgiver = this.daglisteArbeidsgiver.filter { it.dagtype != "ArbeidIkkeGjenopptattDag" }

    val faktiskFomGammel = finnFaktiskFom(dagerArbeidsgiver, dagerPerson, this.vedtak.fom)
    val faktiskFomNy = finnFaktiskFom(daglisteArbeidsgiver, daglisteSykmeldt, this.vedtak.fom)

    if (faktiskFomNy != faktiskFomGammel) {
        logger().warn(
            "Fant ulik fom i daglisteArbeidsgiver og daglisteSykmeldt: $faktiskFomNy og $faktiskFomGammel. ${this.vedtak.utbetaling.utbetalingId}",
        )
    }

    return this.copy(
        dagerArbeidsgiver = dagerArbeidsgiver,
        dagerPerson = dagerPerson,
        daglisteArbeidsgiver = daglisteArbeidsgiver,
        daglisteSykmeldt = daglisteSykmeldt,
        vedtak = this.vedtak.copy(fom = faktiskFomGammel),
    )
}

fun finnFaktiskFom(
    dagerArbeidsgiver: List<RSDag>,
    dagerPerson: List<RSDag>,
    fom: LocalDate,
): LocalDate {
    val tidligsteDagerDag = (dagerArbeidsgiver + dagerPerson).minByOrNull { it.dato }
    if (tidligsteDagerDag != null) {
        if (tidligsteDagerDag.dato.isAfter(fom)) {
            return tidligsteDagerDag.dato
        }
    }
    return fom
}
