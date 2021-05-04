package no.nav.helse.flex.vedtak.api

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.vedtak.service.MottaUtbetalingService
import no.nav.helse.flex.vedtak.service.RetroMottaVedtakService
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.time.Instant
import java.util.*

@Controller
@RequestMapping("/api/v2/mock")
@Unprotected
class VedtakV2TestdataController(
    val mottaVedtakService: RetroMottaVedtakService,
    val mottaUtbetalingService: MottaUtbetalingService,
    val environmentToggles: EnvironmentToggles,
) {

    data class Melding(val melding: String) // Fordi flex-proxy trenger json
    data class VedtakV2(val vedtak: String, val utbetaling: String?)

    @PostMapping("/vedtak/{fnr}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun opprettVedtakV2(@PathVariable fnr: String, @RequestBody vedtakV2: VedtakV2): Melding {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Dette apiet er ikke på i produksjon")
        }
        val vedtakId = UUID.randomUUID()
        mottaVedtakService.mottaVedtak(
            id = vedtakId,
            fnr = fnr,
            vedtak = vedtakV2.vedtak,
            opprettet = Instant.now()
        )

        if (vedtakV2.utbetaling != null) {
            mottaUtbetalingService.mottaUtbetaling(
                fnr = fnr,
                utbetaling = vedtakV2.utbetaling,
                opprettet = Instant.now()
            )
        }

        return Melding("Vedtak med $vedtakId opprettet")
    }
}
