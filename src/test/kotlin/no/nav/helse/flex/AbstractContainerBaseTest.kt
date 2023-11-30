package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.service.SendVedtakStatus
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import okhttp3.mockwebserver.MockWebServer
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
import kotlin.concurrent.thread

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
    lateinit var istilgangskontrollRestTemplate: RestTemplate

    @Autowired
    lateinit var sendVedtakStatus: SendVedtakStatus

    @Autowired
    lateinit var vedtakKafkaConsumer: Consumer<String, String>

    var istilgangskontrollMockRestServiceServer: MockRestServiceServer? = null

    @PostConstruct
    fun setupRestServiceServers() {
        if (istilgangskontrollMockRestServiceServer == null) {
            istilgangskontrollMockRestServiceServer =
                MockRestServiceServer.createServer(istilgangskontrollRestTemplate)
        }
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

    fun authMedSpesifiktAcrClaim(fnr: String, acrClaim: String): String {
        val responseCode = mockMvc.perform(
            get("/api/v3/vedtak")
                .header("Authorization", "Bearer ${tokenxToken(fnr, acrClaim)}")
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn().response.status

        return responseCode.toString()
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

        val map: Map<String, String> = objectMapper.readValue(json)
        return map["status"]!!
    }

    fun settUtbetalingKlarTilVisning() {
        sendVedtakStatus.prosesserUtbetalinger()
    }

    companion object {

        val pdlMockWebserver: MockWebServer

        init {
            val threads = mutableListOf<Thread>()
            thread {
                PostgreSQLContainer11().apply {
                    // Cloud SQL har wal_level = 'logical' på grunn av flagget cloudsql.logical_decoding i
                    // naiserator.yaml. Vi må sette det samme lokalt for at flyway migrering skal fungere.
                    withCommand("postgres", "-c", "wal_level=logical")
                    start()
                    System.setProperty("spring.datasource.url", jdbcUrl)
                    System.setProperty("spring.datasource.username", username)
                    System.setProperty("spring.datasource.password", password)
                }
            }.also { threads.add(it) }

            thread {
                KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.1")).apply {
                    start()
                    System.setProperty("on-prem-kafka.bootstrap-servers", bootstrapServers)
                    System.setProperty("KAFKA_BROKERS", bootstrapServers)
                }
            }.also { threads.add(it) }

            pdlMockWebserver = MockWebServer().apply {
                System.setProperty("pdl.api.url", "http://localhost:$port")
                dispatcher = PdlMockDispatcher
            }

            threads.forEach { it.join() }
        }
    }

    @AfterAll
    fun opprydning() {
        utbetalingRepository.deleteAll()
        vedtakRepository.deleteAll()
        namedParameterJdbcTemplate.update("DELETE FROM ANNULLERING", MapSqlParameterSource())
    }

    @BeforeAll
    fun `Vi subscriber til kafka topicet før testene kjører`() {
        vedtakKafkaConsumer.subscribeHvisIkkeSubscribed(VEDTAK_TOPIC)
    }

    fun tokenxToken(
        fnr: String,
        acrClaim: String = "idporten-loa-high",
        audience: String = "spinnsyn-backend-client-id",
        issuerId: String = "tokenx",
        clientId: String = "spinnsyn-frontend",
        claims: Map<String, Any> = mapOf(
            "acr" to acrClaim,
            "idp" to "idporten",
            "client_id" to clientId,
            "pid" to fnr
        )
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
    acrClaim: String = "Level4",
    subject: String,
    issuerId: String,
    clientId: String = UUID.randomUUID().toString(),
    audience: String,
    claims: Map<String, Any> = mapOf("acr" to acrClaim)
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
