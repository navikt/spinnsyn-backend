package no.nav.helse.flex

import no.nav.helse.flex.cronjob.AntallVedtakJob
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class AntallVedtakCronJobTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Autowired
    lateinit var antallVedtakJob: AntallVedtakJob

    @Value("\${on-prem-kafka.username}")
    lateinit var systembruker: String

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987"
    final val now = LocalDate.now()

    val utbetaling = UtbetalingUtbetalt(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now,
        tom = now.plusDays(1),
        utbetalingId = "124542",
        antallVedtak = 2,
        event = "eventet",
        forbrukteSykedager = 42,
        gjenståendeSykedager = 3254,
        automatiskBehandling = true,
        arbeidsgiverOppdrag = UtbetalingUtbetalt.OppdragDto(
            mottaker = org,
            fagområde = "SPREF",
            fagsystemId = "1234",
            nettoBeløp = 123,
            utbetalingslinjer = emptyList()
        ),
        type = "UTBETALING",
        utbetalingsdager = emptyList()
    )

    @Test
    fun `Mottak av en utbetaling med ett vedtak legges riktig inn i databasen`() {
        val utbetalingId = "oifj2"

        leggMeldingerPåKafka(utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId))

        utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
            .antallVedtak
            .shouldBeEqualTo(1)
    }

    @Test
    fun `Mottak av en utbetaling med to vedtak legges riktig inn i databasen`() {
        val utbetalingId = "02j34g3gj"

        leggMeldingerPåKafka(utbetaling.copy(antallVedtak = 2, utbetalingId = utbetalingId))

        utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
            .antallVedtak
            .shouldBeEqualTo(2)
    }

    @Test
    fun `Utbetaling mottat før antall vedtak ble satt`() {
        val utbetalingId = "983h4f"

        leggMeldingerPåKafka(utbetaling.copy(antallVedtak = 2, utbetalingId = utbetalingId))

        val dbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
        val utbetaling = dbRecord.utbetaling.tilUtbetalingUtbetalt()
        val dbRecordFørMigrering = dbRecord.copy(
            antallVedtak = -1,
            utbetaling = utbetaling.copy(
                antallVedtak = 2,
            ).serialisertTilString()
        )

        utbetalingRepository.save(dbRecordFørMigrering)

        antallVedtakJob.migrate()

        utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
            .antallVedtak
            .shouldBeEqualTo(2)
    }

    @Test
    fun `Utbetaling mottat før antall vedtak ble satt og som ikke har antall vedtak`() {
        val utbetalingId = "fwerf322"

        leggMeldingerPåKafka(utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId))

        val dbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
        val utbetaling = dbRecord.utbetaling.tilUtbetalingUtbetalt()
        val dbRecordFørMigrering = dbRecord.copy(
            antallVedtak = -1,
            utbetaling = utbetaling.copy(
                antallVedtak = null,
            ).serialisertTilString()
        )

        utbetalingRepository.save(dbRecordFørMigrering)

        antallVedtakJob.migrate()

        utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
            .antallVedtak
            .shouldBeEqualTo(1)
    }

    private fun leggMeldingerPåKafka(utbetaling: UtbetalingUtbetalt) {
        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
                .firstOrNull {
                    it.utbetalingId == utbetaling.utbetalingId
                } != null
        }
    }
}
