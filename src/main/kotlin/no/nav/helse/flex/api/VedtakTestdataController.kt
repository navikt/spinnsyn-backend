package no.nav.helse.flex.api

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.MottaUtbetalingService
import no.nav.helse.flex.service.MottaVedtakService
import no.nav.helse.flex.service.RetroMottaVedtakService
import no.nav.helse.flex.service.VedtakNullstillService
import no.nav.helse.flex.service.VedtakStatusService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

@Controller
@RequestMapping("/api/v1/testdata")
class VedtakTestdataController(
    val retroMottaVedtakService: RetroMottaVedtakService,
    val environmentToggles: EnvironmentToggles,
    val vedtakNullstillService: VedtakNullstillService,
    val mottaVedtakService: MottaVedtakService,
    val tokenValidationContextHolder: TokenValidationContextHolder,
    val mottaUtbetalingService: MottaUtbetalingService,
    val vedtakStatusService: VedtakStatusService
) {
    private val log = logger()
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

        mottaVedtakService.mottaVedtak(
            fnr = fnr,
            vedtak = vedtakV2.vedtak,
            timestamp = Instant.now(),
        )

        if (vedtakV2.utbetaling != null) {
            mottaUtbetalingService.mottaUtbetaling(
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

        val antall = vedtakNullstillService.nullstill(fnr)
        return "Slettet $antall vedtak på $fnr"
    }
}
