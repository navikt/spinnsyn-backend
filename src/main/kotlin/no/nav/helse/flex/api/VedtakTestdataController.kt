package no.nav.helse.flex.api

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.service.RetroMottaVedtakService
import no.nav.helse.flex.service.VedtakNullstillService
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.time.Instant
import java.util.*

@Controller
@RequestMapping("/api/v1/mock")
@Unprotected
class VedtakTestdataController(
    val retroMottaVedtakService: RetroMottaVedtakService,
    val environmentToggles: EnvironmentToggles,
    val vedtakNullstillService: VedtakNullstillService,
) {

    data class Melding(val melding: String) // Fordi flex-proxy trenger json

    @PostMapping("/vedtak/{fnr}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun opprettVedtak(@PathVariable fnr: String, @RequestBody vedtak: String): Melding {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Dette apiet er ikke på i produksjon")
        }
        val vedtakId = UUID.randomUUID()
        retroMottaVedtakService.mottaVedtak(
            id = vedtakId,
            fnr = fnr,
            vedtak = vedtak,
            opprettet = Instant.now()
        )
        return Melding("Vedtak med $vedtakId opprettet")
    }

    @PostMapping("/annullering/{fnr}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun opprettAnnullering(@PathVariable fnr: String, @RequestBody annullering: String): Melding {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Dette apiet er ikke på i produksjon")
        }
        val annulleringId = UUID.randomUUID()
        retroMottaVedtakService.mottaAnnullering(
            id = annulleringId,
            fnr = fnr,
            annullering = annullering,
            opprettet = Instant.now()
        )
        return Melding("Annullering med $annulleringId opprettet")
    }

    @DeleteMapping("/vedtak/{fnr}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun slettVedtak(@PathVariable fnr: String): Melding {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Dette apiet er ikke på i produksjon")
        }
        val antall = vedtakNullstillService.nullstill(fnr)
        return Melding("Slettet $antall")
    }
}
