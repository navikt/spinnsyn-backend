package no.nav.helse.flex

import no.nav.helse.flex.cronjob.DataMigreringJob
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DataMigreringCronJobTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Autowired
    lateinit var brukernotifikasjonService: BrukernotifikasjonService

    @Autowired
    lateinit var dataMigreringJob: DataMigreringJob

    @Value("\${on-prem-kafka.username}")
    lateinit var systembruker: String

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987"
    final val now = LocalDate.now()
    val vedtak1 = VedtakFattetForEksternDto(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now,
        tom = now,
        skjæringstidspunkt = now,
        dokumenter = emptyList(),
        inntekt = 0.0,
        sykepengegrunnlag = 0.0,
        utbetalingId = "124542"
    )

    val vedtak2 = VedtakFattetForEksternDto(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now.plusDays(1),
        tom = now.plusDays(5),
        skjæringstidspunkt = now,
        dokumenter = emptyList(),
        inntekt = 0.0,
        sykepengegrunnlag = 0.0,
        utbetalingId = "124542"
    )

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
    fun `Ett vedtak og en utbetaling brukernotifikasjon sendt før lesing`() {
        val utbetalingId = "oifj2"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId)
            ),
        )

        sendBrukernotifikasjon(1)
        lesVarsletVedtak()
        dataMigreringJob.run()

        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
        utbetalingDbRecord.lest.shouldNotBeNull()
        utbetalingDbRecord.brukernotifikasjonSendt.shouldNotBeNull()
        utbetalingDbRecord.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId))
    }

    @Test
    fun `Ett vedtak og en utbetaling lest før brukernotifikasjon`() {
        val utbetalingId = "2f323f"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId)
            ),
        )

        lesVedtakUtenVarsel()
        sendBrukernotifikasjon(0)
        dataMigreringJob.run()

        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
        utbetalingDbRecord.lest.shouldNotBeNull()
        utbetalingDbRecord.brukernotifikasjonSendt.shouldBeNull()
        utbetalingDbRecord.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId))
    }

    @Test
    fun `Ett vedtak og en utbetaling ikke lest`() {
        val utbetalingId = "f33e3"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId)
            ),
        )

        sendBrukernotifikasjon(1)
        dataMigreringJob.run()

        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
        utbetalingDbRecord.lest.shouldBeNull()
        utbetalingDbRecord.brukernotifikasjonSendt.shouldNotBeNull()
        utbetalingDbRecord.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId))
    }

    @Test
    fun `To vedtak og en utbetaling brukernotifikasjon sendt før lesing`() {
        val utbetalingId = "sdop87s"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 2, utbetalingId = utbetalingId),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId),
                vedtak2.copy(utbetalingId = utbetalingId),
            ),
        )

        sendBrukernotifikasjon(1)
        lesVarsletVedtak()
        dataMigreringJob.run()

        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }

        utbetalingDbRecord.lest.shouldNotBeNull()
        utbetalingDbRecord.brukernotifikasjonSendt.shouldNotBeNull()
        utbetalingDbRecord.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId))
    }

    @Test
    fun `To vedtak og en utbetaling lest før brukernotifikasjon`() {
        val utbetalingId = "sp89f7s8"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 2, utbetalingId = utbetalingId),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId),
                vedtak2.copy(utbetalingId = utbetalingId)
            ),
        )

        lesVedtakUtenVarsel()
        sendBrukernotifikasjon(0)
        dataMigreringJob.run()

        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
        utbetalingDbRecord.lest.shouldNotBeNull()
        utbetalingDbRecord.brukernotifikasjonSendt.shouldBeNull()
        utbetalingDbRecord.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId))
    }

    @Test
    fun `To vedtak og en utbetaling ikke lest`() {
        val utbetalingId = "asd7654das"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 2, utbetalingId = utbetalingId),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId),
                vedtak2.copy(utbetalingId = utbetalingId)
            ),
        )

        sendBrukernotifikasjon(1)
        dataMigreringJob.run()

        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
        utbetalingDbRecord.lest.shouldBeNull()
        utbetalingDbRecord.brukernotifikasjonSendt.shouldNotBeNull()
        utbetalingDbRecord.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId))
    }

    @Test
    fun `To vedtak og en utbetaling som ikke er utbetaling eller revurdering`() {
        val utbetalingId = "f786dsffa"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 2, utbetalingId = utbetalingId, type = "TEST"),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId),
                vedtak2.copy(utbetalingId = utbetalingId)
            ),
        )

        sendBrukernotifikasjon(0)
        dataMigreringJob.run()

        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }
        utbetalingDbRecord.lest.shouldBeNull()
        utbetalingDbRecord.brukernotifikasjonSendt.shouldBeNull()
        utbetalingDbRecord.brukernotifikasjonUtelatt.shouldNotBeNull()
        utbetalingDbRecord.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId))
    }

    @Test
    fun `Ett av to vedtak mottatt blir ikke forsøkt migrert`() {
        val utbetalingId = "k0psdop8dads7s"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 2, utbetalingId = utbetalingId),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId)
            ),
        )

        sendBrukernotifikasjon(0)
        dataMigreringJob.run()

        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId }

        utbetalingDbRecord.lest.shouldBeNull()
        utbetalingDbRecord.brukernotifikasjonSendt.shouldBeNull()
        utbetalingDbRecord.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord.varsletMed.shouldBeNull()
    }

    @Test
    fun `Flere utbetalinger enn batchSize`() {
        val utbetalingId1 = "k0psdop8dads1"
        val utbetalingId2 = "k0psdop8dads2"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId1),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId1)
            ),
        )

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId2),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId2)
            ),
        )

        sendBrukernotifikasjon(2)
        dataMigreringJob.migrate(1)

        val utbetalingDbRecord1 = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId1 }

        val utbetalingDbRecord2 = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId2 }

        utbetalingDbRecord1.lest.shouldBeNull()
        utbetalingDbRecord1.brukernotifikasjonSendt.shouldNotBeNull()
        utbetalingDbRecord1.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord1.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId1))

        utbetalingDbRecord2.lest.shouldBeNull()
        utbetalingDbRecord2.brukernotifikasjonSendt.shouldNotBeNull()
        utbetalingDbRecord2.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord2.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId2))
    }

    @Test
    fun `Flere utbetalinger enn batchSize og en utbetaling som mangler vedtak`() {
        val utbetalingId1 = "93fh3u9fr"
        val utbetalingId2 = "3f0j3ifi3"

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId1),
            vedtakene = listOf(
                vedtak1.copy(utbetalingId = utbetalingId1)
            ),
        )

        leggMeldingerPåKafka(
            utbetaling = utbetaling.copy(antallVedtak = 1, utbetalingId = utbetalingId2),
            vedtakene = emptyList(),
        )

        sendBrukernotifikasjon(1)
        dataMigreringJob.migrate(1)

        val utbetalingDbRecord1 = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId1 }

        val utbetalingDbRecord2 = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == utbetalingId2 }

        utbetalingDbRecord1.lest.shouldBeNull()
        utbetalingDbRecord1.brukernotifikasjonSendt.shouldNotBeNull()
        utbetalingDbRecord1.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord1.varsletMed.shouldBeEqualTo(finnVedtaksIdForUtbetaling(utbetalingId1))

        utbetalingDbRecord2.lest.shouldBeNull()
        utbetalingDbRecord2.brukernotifikasjonSendt.shouldBeNull()
        utbetalingDbRecord2.brukernotifikasjonUtelatt.shouldBeNull()
        utbetalingDbRecord2.varsletMed.shouldBeNull()
    }

    private fun leggMeldingerPåKafka(
        vedtakene: List<VedtakFattetForEksternDto>,
        utbetaling: UtbetalingUtbetalt
    ) {
        vedtakene.forEach {
            kafkaProducer.send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    it.serialisertTilString()
                )
            ).get()
        }

        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr)
                .filter { it.utbetalingId == utbetaling.utbetalingId }
                .size == vedtakene.size
        }

        await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
                .firstOrNull {
                    it.utbetalingId == utbetaling.utbetalingId
                } != null
        }
    }

    private fun sendBrukernotifikasjon(forventetAntall: Int) {
        brukernotifikasjonService.prosseserVedtak() `should be equal to` forventetAntall

        val oppgaver = oppgaveKafkaConsumer.ventPåRecords(antall = forventetAntall)

        oppgaver.shouldHaveSize(forventetAntall)
    }

    private fun lesVarsletVedtak() {
        val vedtaketSomIkkeErLest = hentVedtak(fnr).first { !it.lest }
        val vedtaksId = vedtaketSomIkkeErLest.id
        lesVedtak(fnr, vedtaksId).shouldBeEqualTo("Leste vedtak $vedtaksId")
        doneKafkaConsumer.ventPåRecords(antall = 1)
    }

    private fun lesVedtakUtenVarsel() {
        val vedtaketSomIkkeErLest = hentVedtak(fnr).first { !it.lest }
        val vedtaksId = vedtaketSomIkkeErLest.id
        lesVedtak(fnr, vedtaksId).shouldBeEqualTo("Leste vedtak $vedtaksId")
    }

    private fun finnVedtaksIdForUtbetaling(utbetalingId: String) =
        vedtakRepository.findByUtbetalingId(utbetalingId)
            .toList()
            .minByOrNull { it.id!! }!!.id
}
