package no.nav.helse.flex.api

import no.nav.helse.flex.client.SyfoTilgangskontrollOboClient
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.service.VedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
class VedtakVeilederAadV4Controller(
    private val clientIdValidation: ClientIdValidation,
    private val vedtakService: VedtakService,
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
) {

    @GetMapping("/api/v4/veileder/vedtak", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "azureator")
    fun hentVedtak(@RequestHeader("sykmeldt-fnr") fnr: String): List<RSVedtakWrapper> {
        clientIdValidation.validateClientId(
            listOf(
                NamespaceAndApp(
                    namespace = "teamsykefravr",
                    app = "syfomodiaperson"
                ),
                NamespaceAndApp(
                    namespace = "flex",
                    app = "spinnsyn-frontend-interne"
                )
            )
        )

        if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(fnr)) {
            throw IkkeTilgangException()
        }
        return vedtakService.hentVedtak(fnr)
    }
}
