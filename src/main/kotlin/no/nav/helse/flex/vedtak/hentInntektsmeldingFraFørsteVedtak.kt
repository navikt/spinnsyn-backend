package no.nav.helse.flex.vedtak

import no.nav.helse.flex.vedtak.db.Vedtak
import no.nav.helse.flex.vedtak.domene.Dokument
import java.time.LocalDate

fun List<Vedtak>.hentInntektsmeldingFraFørsteVedtak(): List<Vedtak> {
    return this.map {
        if (it.harInntektsmelding()) {
            return@map it
        }
        val førsteVedtaksInntektsmelding = this.finnFørsteVedtak(it).vedtak.dokumenter.find { dokument -> dokument.type == Dokument.Type.Inntektsmelding }
        return@map it.medInntektsmeldingId(førsteVedtaksInntektsmelding)
    }
}

private fun List<Vedtak>.finnForrigeVedtak(vedtak: Vedtak): Vedtak? =
    this.find { it.orgnummer() == vedtak.orgnummer() && it.vedtak.tom.erDagenFør(vedtak.vedtak.fom) }

private fun Vedtak.orgnummer(): String? {
    return this.vedtak.utbetalinger.find { it.fagområde == "SPREF" }?.mottaker
}

private fun List<Vedtak>.finnFørsteVedtak(vedtak: Vedtak): Vedtak {
    var vedtaket = vedtak

    while (this.finnForrigeVedtak(vedtaket) != null) {
        vedtaket = this.finnForrigeVedtak(vedtaket)!!
    }
    return vedtaket
}

private fun Vedtak.harInntektsmelding(): Boolean {
    return this.vedtak.dokumenter.find { it.type == Dokument.Type.Inntektsmelding } != null
}

private fun Vedtak.medInntektsmeldingId(inntektsmelding: Dokument?): Vedtak {
    if (inntektsmelding == null) {
        return this
    }
    return this.copy(vedtak = this.vedtak.copy(dokumenter = this.vedtak.dokumenter.toMutableList().also { dokumentliste -> dokumentliste.add(inntektsmelding) }))
}

private fun LocalDate.erDagenFør(dag: LocalDate): Boolean = this.plusDays(1) == dag
