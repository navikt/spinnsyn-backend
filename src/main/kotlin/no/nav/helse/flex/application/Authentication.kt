package no.nav.helse.flex.application

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.flex.log

fun Application.setupAuth(
    selvbetjeningIssuer: JwtIssuer,
    veilederIssuer: JwtIssuer
) {
    install(Authentication) {
        configureJwtValidation(selvbetjeningIssuer)
        configureJwtValidation(veilederIssuer)
    }
}

private fun Authentication.Configuration.configureJwtValidation(issuer: JwtIssuer) {
    jwt(name = issuer.issuerInternalId.name) {
        verifier(jwkProvider = issuer.jwkProvider, issuer = issuer.wellKnown.issuer)
        validate { credentials: JWTCredential ->
            if (!hasExpectedAudience(credentials, issuer.expectedAudience)) {
                log.warn(
                    "Auth: Unexpected audience for jwt {}, {}",
                    StructuredArguments.keyValue("issuer", credentials.payload.issuer),
                    StructuredArguments.keyValue("audience", credentials.payload.audience)
                )
                return@validate null
            }
            if (!(issuer.issuerInternalId == IssuerInternalId.veileder || erNiva4(credentials))) {
                return@validate null
            }
            return@validate JWTPrincipal(credentials.payload)
        }
    }
}

fun hasExpectedAudience(credentials: JWTCredential, expectedAudience: List<String>): Boolean {
    return expectedAudience.any { credentials.payload.audience.contains(it) }
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}
