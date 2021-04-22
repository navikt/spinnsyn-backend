package no.nav.helse.flex.client

import org.springframework.stereotype.Component

@Component
class SyfoTilgangskontrollClient {
    fun sjekkTilgangVeilederToken(veilederToken: String, fnr: String): Tilgang {
        // TODO
        return Tilgang(harTilgang = true, begrunnelse = null)
    }

    data class Tilgang(
        val harTilgang: Boolean,
        val begrunnelse: String?
    )
}
