package no.nav.helse.flex.vedtaktype

import no.nav.helse.flex.domene.RSVedtakWrapper
import org.springframework.stereotype.Component

@Component
class Vedtaktype {
    private fun RSVedtakWrapper.erRefusjon() = this.sykepengebelopArbeidsgiver > 0

    private fun RSVedtakWrapper.erBrukerutbetaling() = this.sykepengebelopPerson > 0

    private fun RSVedtakWrapper.erKombinasjonutbetaling() = erRefusjon() && erBrukerutbetaling()

    private fun RSVedtakWrapper.erAvvist() = (dagerPerson + dagerArbeidsgiver).any { it.dagtype == "AvvistDag" }

    private fun RSVedtakWrapper.erDelvisAvvist() = (erRefusjon() || erBrukerutbetaling()) && erAvvist()

    private fun RSVedtakWrapper.erHeltAvvist() = !erRefusjon() && !erBrukerutbetaling() && erAvvist()

    private fun RSVedtakWrapper.erRevurdering() = this.vedtak.utbetaling.utbetalingType == "REVURDERING"

    private fun RSVedtakWrapper.erAnnullert() = this.annullert

    fun finnVedtaktype(
        v: RSVedtakWrapper
    ): String {
        if (v.erHeltAvvist()) return "HELT_AVVIST".medSuffiks(v)

        if (v.erKombinasjonutbetaling() && v.erDelvisAvvist()) return "KOMBINASJON_DELVIS_AVVIST".medSuffiks(v)

        if (v.erKombinasjonutbetaling()) return "KOMBINASJON_UTBETALING".medSuffiks(v)

        if (v.erRefusjon() && v.erDelvisAvvist()) return "SPREF_DELVIS_AVVIST".medSuffiks(v)

        if (v.erRefusjon()) return "SPREF_UTBETALING".medSuffiks(v)

        if (v.erBrukerutbetaling() && v.erDelvisAvvist()) return "SP_DELVIS_AVVIST".medSuffiks(v)

        if (v.erBrukerutbetaling()) return "SP_UTBETALING".medSuffiks(v)

        return "VET_IKKE"
    }

    private fun String.medSuffiks(v: RSVedtakWrapper): String {
        if (v.erRevurdering()) return this + "_REVURDERING"
        if (v.erAnnullert()) return this + "_ANNULLERT"
        return this
    }
}
