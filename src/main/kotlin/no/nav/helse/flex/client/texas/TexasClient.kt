package no.nav.helse.flex.client.texas

import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

data class TexasRequest(
    val identity_provider: String,
    val token: String,
)

data class TexasResponse(
    val active: Boolean,
    val error: String? = null,
    val NAVident: String? = null,
    val scp: String? = null,
    val groups: List<String> = emptyList(),
)

interface TexasClient {
    fun introspect(
        identityProvider: String,
        token: String,
    ): TexasResponse
}

@Component
@Profile("!test")
class TexasEksternClient(
    private val texasRestClient: RestClient,
) : TexasClient {
    override fun introspect(
        identityProvider: String,
        token: String,
    ): TexasResponse =
        texasRestClient
            .post()
            .uri { it.build() }
            .contentType(MediaType.APPLICATION_JSON)
            .body(TexasRequest(identity_provider = identityProvider, token = token))
            .retrieve()
            .toEntity<TexasResponse>()
            .body
            ?: throw IllegalStateException("Texas introspection mangler body")
}
