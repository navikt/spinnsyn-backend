package no.nav.helse.flex.vedtak.api

import io.ktor.application.call
import io.ktor.application.ApplicationCall
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.Environment
import no.nav.helse.flex.log
import no.nav.helse.flex.vedtak.service.SyfoTilgangskontrollService
import no.nav.helse.flex.vedtak.service.VedtakService

@KtorExperimentalAPI
fun Route.registerVeilederVedtakApi(
    vedtakService: VedtakService,
    syfoTilgangskontrollService: SyfoTilgangskontrollService,
    environment: Environment
) {
    route("/api/v1/veileder") {
        get("/vedtak") {
            if (environment.isProd()) {
                log.error("Veileder api avslått i produksjon frem til tilgangskontroll er på plass")
                call.respond(Melding("APIet er ikke skrudd på i produksjon").tilRespons(HttpStatusCode.BadRequest))
                return@get
            }

            val fnr = call.request.queryParameters["fnr"]
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer")
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
                    val tilgang = syfoTilgangskontrollService.harTilgangTilBruker(fnr, call.tokenSomString())
                    when(tilgang.harTilgang) {
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

fun ApplicationCall.tokenSomString(): String {
    val principal = principal<JWTPrincipal>()
    return principal.toString()
}
