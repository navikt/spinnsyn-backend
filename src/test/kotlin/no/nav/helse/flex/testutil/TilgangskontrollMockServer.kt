package no.nav.helse.flex.testutil
/*

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.helse.flex.vedtak.service.Tilgang
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun mockSyfotilgangskontrollServer(port: Int, fnr: String): ApplicationEngine {

    val log: Logger = LoggerFactory.getLogger("no.nav.syfo.spinnsyn-backend")

    return embeddedServer(Netty, port) {
        log.info("Starter tilgangskontroll mock server")
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
        routing {
            get("/syfo-tilgangskontroll/api/tilgang/bruker") {
                val fodselsnummer: String? = call.request.queryParameters["fnr"]!!
                if (fodselsnummer == fnr) {
                    call.respond(Tilgang(true, "Veileder har tilgang til bruker"))
                } else {
                    call.respond(Tilgang(false, "Ops! veileder har ikke tilgang til bruker"))
                }
            }
        }
    }
}
*/
