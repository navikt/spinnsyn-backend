package no.nav.helse.flex.arkivering

import no.nav.helse.flex.AbstractContainerBaseTest
import no.nav.helse.flex.kafka.VEDTAK_ARKIVERING_TOPIC
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.postgresql.util.PGobject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VedtakArkiveringTest() : AbstractContainerBaseTest() {

    @Autowired
    private lateinit var arkiveringKafkaConsumer: Consumer<String, String>

    @Autowired
    private lateinit var vedtakArkiveringJob: VedtakArkiveringJob

    @BeforeAll
    fun subscribeTilTopic() {
        arkiveringKafkaConsumer.lyttPaaTopic(VEDTAK_ARKIVERING_TOPIC)
        arkiveringKafkaConsumer.hentRecords().shouldBeEmpty()
    }

    @Test
    fun `arkiverer retro vedtak`() {
        opprettRetroVedtak("uuid-1", "fnr-1")
        opprettRetroVedtak("uuid-2", "fnr-1")
        opprettRetroVedtak("uuid-3", "fnr-2")

        vedtakArkiveringJob.arkiverRetroVedtak()

        val records = await().atMost(5, TimeUnit.SECONDS).until(
            { arkiveringKafkaConsumer.ventPåRecords(3) },
            { true }
        )

        records.size `should be equal to` 3

        await().atMost(5, TimeUnit.SECONDS).until {
            tellArkiverteRetroVedtak() == 3
        }
    }

    @Test
    fun `arkiverer utbetaling`() {
        opprettUtbetaling("uuid-1", "fnr-1")
        opprettUtbetaling("uuid-2", "fnr-1")
        opprettUtbetaling("uuid-3", "fnr-2")

        vedtakArkiveringJob.arkiverUtbetalinger()

        val records = await().atMost(5, TimeUnit.SECONDS).until(
            { arkiveringKafkaConsumer.ventPåRecords(3) },
            { true }
        )

        records.size `should be equal to` 3

        await().atMost(5, TimeUnit.SECONDS).until {
            tellArkiverteUtbetalinger() == 3
        }
    }

    private fun <K, V> Consumer<K, V>.lyttPaaTopic(vararg topics: String) {
        if (this.subscription().isEmpty()) {
            this.subscribe(listOf(*topics))
        }
    }

    private fun <K, V> Consumer<K, V>.hentRecords(duration: Duration = Duration.ofMillis(100)): List<ConsumerRecord<K, V>> {
        return this.poll(duration).also {
            this.commitSync()
        }.iterator().asSequence().toList()
    }

    private fun opprettRetroVedtak(id: String, fnr: String) {
        val now = Instant.now()

        val vedtakJSON = PGobject().also { it.type = "json"; it.value = "{}" }

        namedParameterJdbcTemplate.update(
            """
            INSERT INTO vedtak (id, fnr, vedtak, opprettet, varslet, revarslet, mottatt_etter_migrering) 
            VALUES (:id, :fnr, :vedtak, :opprettet, :varslet, :revarslet, false)
        """,
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("fnr", fnr)
                .addValue("vedtak", vedtakJSON)
                .addValue("opprettet", Timestamp.from(now))
                .addValue("varslet", Timestamp.from(now))
                .addValue("revarslet", Timestamp.from(now))
        )
    }

    private fun opprettUtbetaling(id: String, fnr: String) {
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO utbetaling(id, fnr, utbetaling_id, utbetaling_type, utbetaling, opprettet, antall_vedtak) 
            VALUES (:id, :fnr, :utbetaling_id, :utbetaling_type, :utbetaling, :opprettet, :antall_vedtak)
        """,
            MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("fnr", fnr)
                .addValue("utbetaling_id", id)
                .addValue("utbetaling_type", "")
                .addValue("utbetaling", "{}")
                .addValue("opprettet", Timestamp.from(Instant.now()))
                .addValue("antall_vedtak", 1)
        )
    }

    private fun tellArkiverteRetroVedtak(): Int {
        return jdbcTemplate.queryForObject("SELECT count(id) FROM vedtak WHERE arkivert IS TRUE", Int::class.java)!!
    }

    private fun tellArkiverteUtbetalinger(): Int {
        return jdbcTemplate.queryForObject("SELECT count(id) FROM utbetaling WHERE arkivert IS TRUE", Int::class.java)!!
    }
}
