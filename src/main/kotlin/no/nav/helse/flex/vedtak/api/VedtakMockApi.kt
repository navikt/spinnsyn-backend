package no.nav.helse.flex.vedtak.api

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.request.contentType
import io.ktor.request.receiveStream
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.Environment
import no.nav.helse.flex.vedtak.service.VedtakNullstillService
import no.nav.helse.flex.vedtak.service.VedtakService
import java.nio.charset.Charset
import java.time.Instant
import java.util.UUID

@KtorExperimentalAPI
fun Route.registerVedtakMockApi(vedtakService: VedtakService, env: Environment, vedtakNullstillService: VedtakNullstillService) {
    route("/api/v1/mock") {
        post("/vedtak/{fnr}") {
            if (env.isProd()) {
                throw IllegalStateException("Dette apiet er ikke på i produksjon")
            }
            val fnr = call.parameters["fnr"]!!
            val vedtak = call.receiveTextWithCorrectEncoding()
            val vedtakId = UUID.randomUUID()
            vedtakService.mottaVedtak(
                id = vedtakId,
                fnr = fnr,
                vedtak = vedtak,
                opprettet = Instant.now()
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

/**
 * Receive the request as String.
 * If there is no Content-Type in the HTTP header specified use ISO_8859_1 as default charset, see https://www.w3.org/International/articles/http-charset/index#charset.
 * But use UTF-8 as default charset for application/json, see https://tools.ietf.org/html/rfc4627#section-3
 *
 * https://github.com/ktorio/ktor/issues/384
 *
 */
private suspend fun ApplicationCall.receiveTextWithCorrectEncoding(): String {
    fun ContentType.defaultCharset(): Charset = when (this) {
        ContentType.Application.Json -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    val contentType = request.contentType()
    val suitableCharset = contentType.charset() ?: contentType.defaultCharset()
    return receiveStream().bufferedReader(charset = suitableCharset).readText()
}
