package no.nav.helse.flex.client.pdl

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.util.OBJECT_MAPPER
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.util.*

private const val TEMA = "Tema"
private const val TEMA_SYK = "SYK"
private const val IDENT = "ident"

@Component
class PdlClient(
    @Value("\${pdl.api.url}")
    private val pdlApiUrl: String,
    private val pdlRestTemplate: RestTemplate,
) {
    private val hentPersonQuery =
        """
        query(${"$"}ident: ID!){
          hentIdenter(ident: ${"$"}ident, historikk: false) {
            identer {
              ident,
              gruppe
            }
          }
        }
        """.trimIndent()

    @Retryable(exclude = [FunctionalPdlError::class])
    fun hentPerson(ident: String): ResponseData {
        val graphQLRequest =
            GraphQLRequest(
                query = hentPersonQuery,
                variables = Collections.singletonMap(IDENT, ident),
            )

        val responseEntity =
            pdlRestTemplate.exchange(
                "$pdlApiUrl/graphql",
                HttpMethod.POST,
                HttpEntity(requestToJson(graphQLRequest), createHeaderWithTema()),
                String::class.java,
            )

        if (responseEntity.statusCode != HttpStatus.OK) {
            throw RuntimeException("PDL svarer med status ${responseEntity.statusCode} - ${responseEntity.body}")
        }

        val parsedResponse: GetPersonResponse? = responseEntity.body?.let { OBJECT_MAPPER.readValue(it) }

        parsedResponse?.data?.let {
            return it
        }
        throw FunctionalPdlError("Fant ikke person, ingen body eller data. ${parsedResponse.hentErrors()}")
    }

    private val hentIdenterMedHistorikkQuery =
        """
        query(${"$"}ident: ID!){
          hentIdenter(ident: ${"$"}ident, historikk: true) {
            identer {
              ident,
              gruppe
            }
          }
        }
        """.trimIndent()

    @Retryable(exclude = [FunctionalPdlError::class])
    fun hentIdenterMedHistorikk(ident: String): List<PdlIdent> {
        val graphQLRequest =
            GraphQLRequest(
                query = hentIdenterMedHistorikkQuery,
                variables = Collections.singletonMap(IDENT, ident),
            )

        val responseEntity =
            pdlRestTemplate.exchange(
                "$pdlApiUrl/graphql",
                HttpMethod.POST,
                HttpEntity(requestToJson(graphQLRequest), createHeaderWithTema()),
                String::class.java,
            )

        if (responseEntity.statusCode != HttpStatus.OK) {
            throw RuntimeException("PDL svarer med status ${responseEntity.statusCode} - ${responseEntity.body}")
        }

        val parsedResponse: GetPersonResponse? = responseEntity.body?.let { OBJECT_MAPPER.readValue(it) }

        parsedResponse?.data?.let {
            return it.hentIdenter?.identer ?: emptyList()
        }
        throw FunctionalPdlError("Fant ikke person, ingen body eller data. ${parsedResponse.hentErrors()}")
    }

    private fun createHeaderWithTema(): HttpHeaders {
        val headers = createHeader()
        headers[TEMA] = TEMA_SYK
        return headers
    }

    private fun createHeader(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return headers
    }

    private fun requestToJson(graphQLRequest: Any): String =
        try {
            ObjectMapper().writeValueAsString(graphQLRequest)
        } catch (e: JsonProcessingException) {
            throw RuntimeException(e)
        }

    private fun GetPersonResponse?.hentErrors(): String? = this?.errors?.map { it.message }?.joinToString(" - ")

    data class GraphQLRequest(
        val query: String,
        val variables: Map<String, String>,
    )

    class FunctionalPdlError(
        message: String,
    ) : RuntimeException(message)
}
