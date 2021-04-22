package no.nav.helse.flex.client

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class SyfoTilgangskontrollClient(
    @Value("\${syfotilgangskontroll.url}") private val url: String,
    private val restTemplate: RestTemplate
) {
    private val log = logger()

    fun sjekkTilgangVeilederToken(veilederToken: String, fnr: String): Tilgang {
        val uriString = UriComponentsBuilder
            .fromHttpUrl("$url/syfo-tilgangskontroll/api/tilgang/bruker")
            .queryParam("fnr", fnr)
            .toUriString()
        return sjekkTilgang(uriString = uriString, token = veilederToken)
    }

    private fun sjekkTilgang(uriString: String, token: String): Tilgang {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers.set("Authorization", "Bearer $token")

        try {
            val result = restTemplate.exchange(uriString, HttpMethod.GET, HttpEntity<Any>(headers), Tilgang::class.java)

            if (result.statusCode == HttpStatus.OK || result.statusCode == HttpStatus.FORBIDDEN) {
                if (result.body != null) {
                    return result.body!!
                }
            }
            val message = "Kall mot syfotilgangskontroll returnerer ikke forventet data. Status: ${result.statusCode}"
            log.error(message)
            throw RuntimeException(message)
        } catch (e: HttpClientErrorException) {

            if (e.statusCode == HttpStatus.FORBIDDEN) {
                return Tilgang(harTilgang = false, begrunnelse = "Syfo-tilgangskontroll returnerte 403")
            }

            log.error("Feil ved oppslag mot syfotilgangskontroll ${e.rawStatusCode} ${e.responseBodyAsString}", e)
            throw RuntimeException(e)
        }
    }

    data class Tilgang(
        val harTilgang: Boolean,
        val begrunnelse: String?
    )
}
