package no.nav.helse.flex.vedtak.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.vedtak.service.VedtakService

@KtorExperimentalAPI
fun Route.registerVeilederVedtakApi(vedtakService: VedtakService) {
    route("/api/v1/veileder") {
        get("/vedtak") {
            val fnr = call.request.queryParameters["fnr"]
            if (fnr == null) {
                call.respond(Melding("Mangler fnr query param").tilRespons(HttpStatusCode.BadRequest))
            } else {
                call.respond(vedtakService.hentVedtak(fnr))
            }
        }
    }
}
