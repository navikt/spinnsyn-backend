package no.nav.syfo.vedtak.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.vedtak.service.VedtakService


@KtorExperimentalAPI
fun Route.registerVedtakApi(vedtakService: VedtakService) {
    route("/api/v1") {
        get("/vedtak") {
            val principal: JWTPrincipal = call.authentication.principal()!!
            val fnr = principal.payload.subject
            call.respond(vedtakService.hentVedtak(fnr))
        }
    }
}
