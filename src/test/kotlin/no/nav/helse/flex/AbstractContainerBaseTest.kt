package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.service.SendVedtakStatus
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.*
import javax.annotation.PostConstruct

private class PostgreSQLContainer11 : PostgreSQLContainer<PostgreSQLContainer11>("postgres:11.4-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractContainerBaseTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var server: MockOAuth2Server

    @Autowired
    lateinit var utbetalingRepository: UtbetalingRepository

    @Autowired
    lateinit var vedtakRepository: VedtakRepository

    @Autowired
    lateinit var organisasjonRepository: OrganisasjonRepository

    @Autowired
    lateinit var annulleringDAO: AnnulleringDAO

    @Autowired
    lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    lateinit var syfotilgangskontrollRestTemplate: RestTemplate

    @Autowired
    lateinit var sendVedtakStatus: SendVedtakStatus

    var syfotilgangskontrollMockRestServiceServer: MockRestServiceServer? = null

    @PostConstruct
    fun setupRestServiceServers() {
        if (syfotilgangskontrollMockRestServiceServer == null) {
            syfotilgangskontrollMockRestServiceServer =
                MockRestServiceServer.createServer(syfotilgangskontrollRestTemplate)
        }
    }

    fun loginserviceJwt(fnr: String) = server.token(subject = fnr)

    fun hentVedtakMedLoginserviceToken(fnr: String): List<RSVedtakWrapper> {
        settUtbetalingKlarTilVisning()

        val json = mockMvc.perform(
            get("/api/v2/vedtak")
                .header("Authorization", "Bearer ${loginserviceJwt(fnr)}")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    fun hentVedtakMedTokenXToken(fnr: String): List<RSVedtakWrapper> {
        settUtbetalingKlarTilVisning()

        val json = mockMvc.perform(
            get("/api/v3/vedtak")
                .header("Authorization", "Bearer ${tokenxToken(fnr)}")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    fun hentVedtakSomVeilederObo(fnr: String, token: String): List<RSVedtakWrapper> {
        settUtbetalingKlarTilVisning()

        val json = mockMvc.perform(
            get("/api/v3/veileder/vedtak?fnr=$fnr")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    fun hentVedtakSomVeilederOboV4(fnr: String, token: String): List<RSVedtakWrapper> {
        settUtbetalingKlarTilVisning()

        val json = mockMvc.perform(
            get("/api/v4/veileder/vedtak")
                .header("Authorization", "Bearer $token")
                .header("sykmeldt-fnr", fnr)
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    fun hentVedtakSomSpinnsynFrontendArkivering(fnr: String, token: String): List<RSVedtakWrapper> {
        settUtbetalingKlarTilVisning()

        val json = mockMvc.perform(
            get("/api/v1/arkivering/vedtak")
                .header("Authorization", "Bearer $token")
                .header("fnr", fnr)
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    fun lesVedtakMedTokenXToken(fnr: String, id: String): String {
        val json = mockMvc.perform(
            post("/api/v3/vedtak/$id/les")
                .header("Authorization", "Bearer ${tokenxToken(fnr)}")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return json
    }

    fun settUtbetalingKlarTilVisning() {
        sendVedtakStatus.prosesserUtbetalinger()
    }

    companion object {
        init {
            PostgreSQLContainer11().also {
                it.start()
                System.setProperty("spring.datasource.url", it.jdbcUrl)
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.1.0")).also {
                it.start()
                System.setProperty("on-prem-kafka.bootstrap-servers", it.bootstrapServers)
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
            }
        }
    }

    @AfterAll
    fun opprydning() {
        utbetalingRepository.deleteAll()
        vedtakRepository.deleteAll()
        namedParameterJdbcTemplate.update("DELETE FROM ANNULLERING", MapSqlParameterSource())
    }

    fun tokenxToken(
        fnr: String,
        audience: String = "spinnsyn-backend-client-id",
        issuerId: String = "tokenx",
        clientId: String = "spinnsyn-frontend",
        claims: Map<String, Any> = mapOf(
            "acr" to "Level4",
            "idp" to "idporten",
            "client_id" to clientId,
            "pid" to fnr,
        ),
    ): String {

        return server.issueToken(
            issuerId,
            clientId,
            DefaultOAuth2TokenCallback(
                issuerId = issuerId,
                subject = UUID.randomUUID().toString(),
                audience = listOf(audience),
                claims = claims,
                expiry = 3600
            )
        ).serialize()
    }
}

fun MockOAuth2Server.token(
    subject: String,
    issuerId: String = "loginservice",
    clientId: String = UUID.randomUUID().toString(),
    audience: String = "loginservice-client-id",
    claims: Map<String, Any> = mapOf("acr" to "Level4"),
): String {
    return this.issueToken(
        issuerId,
        clientId,
        DefaultOAuth2TokenCallback(
            issuerId = issuerId,
            subject = subject,
            audience = listOf(audience),
            claims = claims,
            expiry = 3600
        )
    ).serialize()
}
