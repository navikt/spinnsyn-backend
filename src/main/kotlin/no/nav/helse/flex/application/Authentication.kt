package no.nav.helse.flex.application

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.flex.log

fun Application.setupAuth(
    selvbetjeningIssuer: JwtIssuer
) {
    install(Authentication) {
        jwt(name = selvbetjeningIssuer.issuerInternalId.name) {
            verifier(jwkProvider = selvbetjeningIssuer.jwkProvider, issuer = selvbetjeningIssuer.wellKnown.issuer)
            validate { credentials ->
                when {
                    hasExpectedAudience(
                        credentials,
                        selvbetjeningIssuer.expectedAudience
                    ) -> JWTPrincipal(credentials.payload)
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

fun hasExpectedAudience(credentials: JWTCredential, expectedAudience: List<String>): Boolean {
    return expectedAudience.any { credentials.payload.audience.contains(it) }
}
