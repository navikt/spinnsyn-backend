package no.nav.helse.flex.vedtak.service

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.Environment
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
class SyfoTilgangskontrollService(
    private val environment: Environment
) {
    val log: Logger = LoggerFactory.getLogger("no.nav.syfo.spinnsyn-backend")

    suspend fun harTilgangTilBruker(fnr: String, token: String): Tilgang {

        val httpClient = HttpClient {
            install(JsonFeature) {
                serializer = JacksonSerializer()
            }
        }

        val response: HttpResponse = httpClient.get("${environment.apiGatewayUrl}/syfo-tilgangskontroll/syfo-tilgangskontroll/api/tilgang/bruker?fnr=$fnr") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("x-nav-apiKey", environment.syfotilgangskontrollApiGwKey)
            accept(ContentType.Application.Json)
        }

        log.info("response cought by service: ${response.status}")
        return when (response.status) {
            HttpStatusCode.OK -> {
                return response.receive()
            }
            else -> {
                log.info("Ingen tilgang, Tilgangskontrollstatus er : {}", response.status)
                Tilgang(false, "Veileder har ingen tilgang til denne brukeren")
            }
        }
    }
}
data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String?
)
