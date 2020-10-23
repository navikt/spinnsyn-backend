package no.nav.helse.flex.vedtak.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.log
import no.nav.helse.flex.vedtak.service.SyfoTilgangskontrollService
import no.nav.helse.flex.vedtak.service.VedtakService

@KtorExperimentalAPI
fun Route.registerVeilederVedtakApi(
    vedtakService: VedtakService,
    syfoTilgangskontrollService: SyfoTilgangskontrollService
) {
    route("/api/v1/veileder") {
        get("/vedtak") {

            val fnr = call.request.queryParameters["fnr"]
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            when {
                token == null -> {
                    call.respond(
                        Melding("Mangler token")
                            .tilRespons(HttpStatusCode.Unauthorized)
                    )
                }
                fnr == null -> {
                    call.respond(
                        Melding("Mangler fnr query param")
                            .tilRespons(HttpStatusCode.BadRequest)
                    )
                }
                else -> {
                    val tilgang = syfoTilgangskontrollService.harTilgangTilBruker(fnr, token)

                    when (tilgang.harTilgang) {
                        false -> {
                            call.respond(
                                Melding("Veileder har ikke tilgang til dennne personen")
                                    .tilRespons(HttpStatusCode.Forbidden)
                            )
                        }
                        else -> {
                            log.info("Godkjent tilgang til bruker. Henter vedtak.")
                            call.respond(vedtakService.hentVedtak(fnr))
                        }
                    }
                }
            }
        }
    }
}
