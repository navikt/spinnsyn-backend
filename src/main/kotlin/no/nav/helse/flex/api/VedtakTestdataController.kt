package no.nav.helse.flex.api

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.service.MottaUtbetaling
import no.nav.helse.flex.service.MottaVedtak
import no.nav.helse.flex.service.NullstillVedtak
import no.nav.helse.flex.service.RetroMottaVedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.time.Instant
import java.util.*

@Controller
@RequestMapping("/api/v1/testdata")
class VedtakTestdataController(
    val retroMottaVedtakService: RetroMottaVedtakService,
    val environmentToggles: EnvironmentToggles,
    val nullstillVedtak: NullstillVedtak,
    val tokenValidationContextHolder: TokenValidationContextHolder,
    val mottaVedtak: MottaVedtak,
    val mottaUtbetaling: MottaUtbetaling,
) {
    data class VedtakV2(val vedtak: String, val utbetaling: String?)

    @PostMapping(
        "/vedtak",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseBody
    @ProtectedWithClaims(issuer = "loginservice", claimMap = ["acr=Level4"])
    fun opprettVedtak(@RequestBody vedtakV2: VedtakV2): String {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Dette apiet er ikke på i produksjon")
        }
        val fnr = tokenValidationContextHolder.fnrFraOIDC()

        mottaVedtak.mottaVedtak(
            fnr = fnr,
            vedtak = vedtakV2.vedtak,
            timestamp = Instant.now(),
        )

        if (vedtakV2.utbetaling != null) {
            mottaUtbetaling.mottaUtbetaling(
                fnr = fnr,
                utbetaling = vedtakV2.utbetaling,
                opprettet = Instant.now()
            )
        }

        return "Vedtak opprettet på $fnr"
    }

    @GetMapping("/fnr", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "loginservice", claimMap = ["acr=Level4"])
    fun hvemErJeg(): String {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Dette apiet er ikke på i produksjon")
        }
        return tokenValidationContextHolder.fnrFraOIDC()
    }

    @PostMapping("/annullering", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "loginservice", claimMap = ["acr=Level4"])

    fun opprettAnnullering(@RequestBody annullering: String): String {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Dette apiet er ikke på i produksjon")
        }
        val fnr = tokenValidationContextHolder.fnrFraOIDC()

        val annulleringId = UUID.randomUUID()
        retroMottaVedtakService.mottaAnnullering(
            id = annulleringId,
            fnr = fnr,
            annullering = annullering,
            opprettet = Instant.now()
        )
        return "Annullering med $annulleringId opprettet på $fnr"
    }

    @DeleteMapping("/vedtak", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "loginservice", claimMap = ["acr=Level4"])
    fun slettVedtak(): String {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Dette apiet er ikke på i produksjon")
        }
        val fnr = tokenValidationContextHolder.fnrFraOIDC()

        val antall = nullstillVedtak.nullstill(fnr)
        return "Slettet $antall utbetalinger og tilhørende vedtak tilhørende fnr: $fnr."
    }
}
