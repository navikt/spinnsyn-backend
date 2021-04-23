package no.nav.helse.flex

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.api.registerNaisApi
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object SelftestSpek : Spek({
    describe("Successfull liveness and readyness tests") {
        with(TestApplicationEngine()) {
            start()
            val applicationState = ApplicationState()
            applicationState.ready = true
            application.routing { registerNaisApi(applicationState) }

            it("Returns ok on internal/health") {
                with(handleRequest(HttpMethod.Get, "/internal/health")) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual "I'm alive! :)"
                }
            }
            it("Returns ok in internal/health") {
                with(handleRequest(HttpMethod.Get, "/internal/health")) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual "I'm alive! :)"
                }
            }
        }
    }
    describe("Unsuccessful liveness and readyness") {
        with(TestApplicationEngine()) {
            start()
            val applicationState = ApplicationState()
            applicationState.ready = false
            application.routing { registerNaisApi(applicationState) }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/health")) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                    response.content shouldEqual "Please wait! I'm not ready :("
                }
            }

            it("Returns internal server error when readyness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/health")) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                    response.content shouldEqual "Please wait! I'm not ready :("
                }
            }
        }
    }
})
