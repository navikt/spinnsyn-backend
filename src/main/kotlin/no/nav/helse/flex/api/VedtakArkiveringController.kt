package no.nav.helse.flex.api

import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.service.VedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
class VedtakArkiveringController(
    private val clientIdValidation: ClientIdValidation,
    private val vedtakService: VedtakService,
) {

    @GetMapping("/api/v1/arkivering/vedtak", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "azureator")
    fun hentVedtak(@RequestHeader fnr: String): List<RSVedtakWrapper> {
        clientIdValidation.validateClientId(
            NamespaceAndApp(
                namespace = "flex",
                app = "spinnsyn-frontend-arkivering"
            )
        )

        return vedtakService.hentVedtak(fnr)
    }
}
