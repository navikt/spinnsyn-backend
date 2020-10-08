package no.nav.helse.flex.vedtak.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.Environment
import no.nav.helse.flex.log
import no.nav.helse.flex.vedtak.service.VedtakService

@KtorExperimentalAPI
fun Route.registerVeilederVedtakApi(vedtakService: VedtakService, environment: Environment) {
    route("/api/v1/veileder") {
        get("/vedtak") {
            if (environment.isProd()) {
                log.error("Veileder api avsltt i produksjon frem til tilgangskontroll er på plass")
                call.respond(Melding("APIet er ikke skurdd på i produksjon").tilRespons(HttpStatusCode.BadRequest))
                return@get
            }
            val fnr = call.request.queryParameters["fnr"]
            if (fnr == null) {
                call.respond(Melding("Mangler fnr query param").tilRespons(HttpStatusCode.BadRequest))
            } else {
                call.respond(vedtakService.hentVedtak(fnr))
            }
        }
    }
}
