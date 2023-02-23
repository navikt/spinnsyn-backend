package no.nav.helse.flex

import java.util.*

fun AbstractContainerBaseTest.buildAzureClaimSet(
    subject: String,
    issuer: String = "azureator",
    audience: String = "spinnsyn-backend-client-id"
): String {
    val claims = HashMap<String, String>()

    return server.token(
        subject = "Vi sjekker azp",
        issuerId = issuer,
        clientId = subject,
        audience = audience,
        claims = claims
    )
}

fun AbstractContainerBaseTest.skapAzureJwt(subject: String = "spinnsyn-frontend-interne-client-id") =
    buildAzureClaimSet(subject = subject)
