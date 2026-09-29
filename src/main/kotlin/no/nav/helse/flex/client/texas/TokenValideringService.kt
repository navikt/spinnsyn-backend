package no.nav.helse.flex.client.texas

import jakarta.servlet.http.HttpServletRequest
import no.nav.helse.flex.api.AbstractApiError
import no.nav.helse.flex.api.LogLevel
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

const val VEDTAK_SCOPE = "vedtak.read"

@Service
class TokenValideringService(
    private val texasClient: TexasClient,
    @param:Value("\${FLEX_GROUP_ID}") private val flexGruppe: String,
) {
    fun validerTilgangOgHentNavIdent(
        token: String?,
        identityProvider: String,
    ): String {
        if (token == null) {
            throw UautorisertException("Fant ikke token i request")
        }

        val respons = texasClient.introspect(identityProvider = identityProvider, token = token)
        if (!respons.active) {
            throw UautorisertException("Ugyldig token")
        }

        val navIdent = respons.NAVident ?: throw UautorisertException("Fant ikke NAVident i token")
        if (flexGruppe !in respons.groups) {
            throw IngenTilgangException("Ansatt er ikke medlem av Flex-gruppen")
        }
        if (VEDTAK_SCOPE !in respons.scp.orEmpty().split(Regex("\\s+"))) {
            throw IngenTilgangException("Token mangler scope $VEDTAK_SCOPE")
        }
        return navIdent
    }
}

fun HttpServletRequest.getToken(): String? = getHeader("Authorization")?.removePrefix("Bearer ")

class IngenTilgangException(
    message: String,
) : AbstractApiError(
        message = message,
        httpStatus = HttpStatus.FORBIDDEN,
        reason = "INGEN_TILGANG",
        loglevel = LogLevel.WARN,
    )

class UautorisertException(
    message: String,
) : AbstractApiError(
        message = message,
        httpStatus = HttpStatus.UNAUTHORIZED,
        reason = "UAUTORISERT",
        loglevel = LogLevel.WARN,
    )
