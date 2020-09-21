package no.nav.helse.flex.vedtak.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.Environment
import no.nav.helse.flex.vedtak.service.VedtakNullstillService
import no.nav.helse.flex.vedtak.service.VedtakService
import java.util.UUID

@KtorExperimentalAPI
fun Route.registerVedtakMockApi(vedtakService: VedtakService, env: Environment, vedtakNullstillService: VedtakNullstillService) {
    route("/api/v1/mock") {
        post("/vedtak/{fnr}") {
            if (env.isProd()) {
                throw IllegalStateException("Dette apiet er ikke på i produksjon")
            }
            val fnr = call.parameters["fnr"]!!
            val vedtak = call.receive<String>()
            val vedtakId = UUID.randomUUID()
            vedtakService.håndterVedtak(
                id = vedtakId,
                fnr = fnr,
                vedtak = vedtak
            )
            call.respond(Melding("Vedtak med $vedtakId opprettet").tilRespons(HttpStatusCode.Created))
        }

        delete("/vedtak/{fnr}") {
            if (env.isProd()) {
                throw IllegalStateException("Dette apiet er ikke på i produksjon")
            }
            val fnr = call.parameters["fnr"]!!
            val antall = vedtakNullstillService.nullstill(fnr)

            call.respond(Melding("Slettet $antall").tilRespons(HttpStatusCode.OK))
        }
    }
}
