package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.brukernotifkasjon.DONE_TOPIC
import no.nav.helse.flex.brukernotifkasjon.OPPGAVE_TOPIC
import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.service.RetroRSVedtak
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.*
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
    lateinit var annulleringDAO: AnnulleringDAO

    @Autowired
    lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    lateinit var syfotilgangskontrollRestTemplate: RestTemplate

    var syfotilgangskontrollMockRestServiceServer: MockRestServiceServer? = null

    @PostConstruct
    fun setupRestServiceServers() {
        if (syfotilgangskontrollMockRestServiceServer == null) {
            syfotilgangskontrollMockRestServiceServer = MockRestServiceServer.createServer(syfotilgangskontrollRestTemplate)
        }
    }

    fun jwt(fnr: String) = server.token(subject = fnr)

    fun veilederToken(uniqueName: String? = null): String {
        val claims = mutableMapOf("acr" to "Level4")
        if (uniqueName != null) {
            claims["unique_name"] = uniqueName
        }

        return server.token(
            subject = "veileder123",
            issuerId = "veileder",
            audience = "veileder-audience",
            claims = claims
        )
    }

    fun hentVedtak(fnr: String): List<RSVedtakWrapper> {
        val json = mockMvc.perform(
            get("/api/v2/vedtak")
                .header("Authorization", "Bearer ${jwt(fnr)}")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    fun hentVedtakSomVeileder(fnr: String, veilederToken: String): List<RetroRSVedtak> {
        val json = mockMvc.perform(
            get("/api/v1/veileder/vedtak?fnr=$fnr")
                .header("Authorization", "Bearer $veilederToken")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    fun hentVedtakSomVeilederObo(fnr: String, token: String): List<RetroRSVedtak> {
        val json = mockMvc.perform(
            get("/api/v2/veileder/vedtak?fnr=$fnr")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    fun lesVedtak(fnr: String, id: String): String {
        val json = mockMvc.perform(
            post("/api/v2/vedtak/$id/les")
                .header("Authorization", "Bearer ${jwt(fnr)}")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return json
    }

    @Autowired
    lateinit var oppgaveKafkaConsumer: Consumer<Nokkel, Oppgave>

    @Autowired
    lateinit var doneKafkaConsumer: Consumer<Nokkel, Done>

    @AfterAll
    fun `Vi leser oppgave kafka topicet og feil hvis noe finnes og slik at subklassetestene leser alt`() {
        oppgaveKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @AfterAll
    fun `Vi leser done kafka topicet og feil hvis noe finnes og slik at subklassetestene leser alt`() {
        doneKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser oppgave og done kafka topicet og feiler om noe eksisterer`() {
        oppgaveKafkaConsumer.subscribeHvisIkkeSubscribed(OPPGAVE_TOPIC)
        doneKafkaConsumer.subscribeHvisIkkeSubscribed(DONE_TOPIC)

        oppgaveKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        doneKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
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

    @Test
    @Order(99)
    fun opprydning() {
        utbetalingRepository.deleteAll()
        vedtakRepository.deleteAll()
        namedParameterJdbcTemplate.update("DELETE FROM ANNULLERING", MapSqlParameterSource())
        namedParameterJdbcTemplate.update("DELETE FROM VEDTAK", MapSqlParameterSource())
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
