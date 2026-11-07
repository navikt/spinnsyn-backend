package no.nav.helse.flex

fun FellesTestOppsett.buildAzureClaimSet(
    subject: String,
    issuer: String = "azureator",
    audience: String = "spinnsyn-backend-client-id",
    claims: Map<String, Any> = emptyMap(),
): String =
    server.token(
        subject = "Vi sjekker azp",
        issuerId = issuer,
        clientId = subject,
        audience = audience,
        claims = claims,
    )

fun FellesTestOppsett.skapAzureJwt(subject: String = "spinnsyn-frontend-interne-client-id") = buildAzureClaimSet(subject = subject)

fun FellesTestOppsett.skapSaksbehandlerJwt(
    subject: String = "spinnsyn-frontend-saksbehandler-client-id",
    grupper: List<String> = listOf("speil-brukere-gruppe"),
    navIdent: String? = "Z999999",
): String =
    buildAzureClaimSet(
        subject = subject,
        claims =
            buildMap {
                put("groups", grupper)
                navIdent?.let { put("NAVident", it) }
            },
    )
