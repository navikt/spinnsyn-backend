package no.nav.helse.flex.application

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.authenticate
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.api.registerNaisApi
import no.nav.helse.flex.application.metrics.monitorHttpRequests
import no.nav.helse.flex.vedtak.api.registerVedtakApi
import no.nav.helse.flex.vedtak.api.registerVedtakMockApi
import no.nav.helse.flex.vedtak.service.VedtakNullstillService
import no.nav.helse.flex.vedtak.service.VedtakService
import java.util.UUID

@KtorExperimentalAPI
fun createApplicationEngine(
    env: Environment,
    vedtakService: VedtakService,
    vedtakNullstillService: VedtakNullstillService,
    selvbetjeningIssuer: JwtIssuer,
    applicationState: ApplicationState
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        configureApplication(
            selvbetjeningIssuer = selvbetjeningIssuer,
            applicationState = applicationState,
            vedtakService = vedtakService,
            env = env,
            vedtakNullstillService = vedtakNullstillService
        )
    }

@KtorExperimentalAPI
fun Application.configureApplication(
    selvbetjeningIssuer: JwtIssuer,
    applicationState: ApplicationState,
    vedtakService: VedtakService,
    env: Environment,
    vedtakNullstillService: VedtakNullstillService
) {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    setupAuth(
        selvbetjeningIssuer = selvbetjeningIssuer
    )
    install(CallId) {
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(HttpHeaders.XCorrelationId)
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            log.error("Caught exception ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
        }
    }

    routing {
        registerNaisApi(applicationState)
        authenticate(IssuerInternalId.selvbetjening.name) {
            registerVedtakApi(vedtakService)
        }
        if (!env.isProd()) {
            registerVedtakMockApi(
                vedtakService = vedtakService,
                vedtakNullstillService = vedtakNullstillService,
                env = env
            )
        }
    }
    intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
}
