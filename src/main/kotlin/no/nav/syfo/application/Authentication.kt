package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log

fun Application.setupAuth(
    loginserviceClientId: String,
    jwkProvider: JwkProvider,
    issuer: String
) {
    install(Authentication) {
        jwt(name = "jwt") {
            verifier(jwkProvider, issuer)
            validate { credentials ->
                when {
                    hasLoginserviceClientIdAudience(credentials, loginserviceClientId) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun hasLoginserviceClientIdAudience(credentials: JWTCredential, loginserviceClientId: String): Boolean {
    return credentials.payload.audience.contains(loginserviceClientId)
}
