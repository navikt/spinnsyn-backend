import no.nav.helse.flex.AbstractContainerBaseTest
import no.nav.helse.flex.Application
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.service.IdentService
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = [Application::class])
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class HentingAvVedtakMedIdentTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    private lateinit var identService: IdentService

    final val fnr1 = "31111111111"
    final val fnr2 = "21111111111"
    final val fnr3 = "11111111111"
    final val aktørId = "321"
    final val org = "987"
    final val now = LocalDate.now()
    final val utbetalingId1 = "124542"
    final val utbetalingId2 = "124543"
    final val utbetalingId3 = "124544"
    final val fom = LocalDate.now().minusDays(7)
    final val tom = LocalDate.now()

    val vedtak1 = lagVedtak(fnr1, aktørId, org, now, utbetalingId1)
    val vedtak2 = lagVedtak(fnr2, aktørId, org, now.plusDays(1), utbetalingId2)
    val vedtak3 = lagVedtak(fnr3, aktørId, org, now.plusDays(2), utbetalingId3)

    val utbetaling1 = lagUtbetaling(fnr1, org, fom, tom, utbetalingId1)
    val utbetaling2 = lagUtbetaling(fnr2, org, fom.plusDays(1), tom.plusDays(1), utbetalingId2)
    val utbetaling3 = lagUtbetaling(fnr3, org, fom.plusDays(2), tom.plusDays(2), utbetalingId3)

    @Test
    @Order(1)
    fun `tre vedtak som er lagret med forskjellige fødselsnummere tilhører samme person`() {
        vedtak1.leggPaKafka()
        vedtak2.leggPaKafka()
        vedtak3.leggPaKafka()

        utbetaling1.leggPaKafka()
        utbetaling2.leggPaKafka()
        utbetaling3.leggPaKafka()

        val hentetVedtak = vedtakRepository.findVedtakDbRecordsByIdenter(listOf(fnr1, fnr2, fnr3))
        hentetVedtak.shouldHaveSize(3)

        val responseData = identService.hentFolkeregisterIdenterMedHistorikkForFnr("31111111111")
        responseData.andreIdenter `should be equal to` listOf("11111111111", "21111111111")
        responseData.originalIdent `should be equal to` "31111111111"

        val vedtakFraService1 = hentVedtakMedTokenXToken(fnr1)

        vedtakFraService1.size `should be equal to` 3
    }

    @Test
    @Order(2)
    fun `vedtak som er lagret med forskjellige fødselsnummer merkeres som lest`() {
        val vedtakNr2iLista = hentVedtakMedTokenXToken(fnr1)[1].id

        lesVedtakMedTokenXToken(fnr1, vedtakNr2iLista)

        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByIdent(listOf(fnr1, fnr2, fnr3)).sortedBy { it.fnr }
        utbetalinger[1].lest.shouldNotBeNull()
    }

    private fun lagVedtak(fnr: String, aktørId: String, org: String, dato: LocalDate, utbetalingId: String): VedtakFattetForEksternDto {
        return VedtakFattetForEksternDto(
            fødselsnummer = fnr,
            aktørId = aktørId,
            organisasjonsnummer = org,
            fom = dato,
            tom = dato.plusDays(5),
            skjæringstidspunkt = dato,
            dokumenter = emptyList(),
            inntekt = 0.0,
            sykepengegrunnlag = 0.0,
            utbetalingId = utbetalingId,
            grunnlagForSykepengegrunnlag = 0.0,
            grunnlagForSykepengegrunnlagPerArbeidsgiver = mutableMapOf("1234" to 0.0),
            begrensning = "VET_IKKE",
            vedtakFattetTidspunkt = LocalDate.now()
        )
    }

    fun VedtakFattetForEksternDto.leggPaKafka() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                this.fødselsnummer,
                this.serialisertTilString()
            )
        ).get()
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(this.fødselsnummer).isNotEmpty()
        }
    }

    fun UtbetalingUtbetalt.leggPaKafka() {
        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                this.fødselsnummer,
                this.serialisertTilString()
            )
        ).get()
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(this.fødselsnummer).isNotEmpty()
        }
    }
    private fun lagUtbetaling(fnr: String, org: String, fom: LocalDate, tom: LocalDate, utbetalingId: String): UtbetalingUtbetalt {
        return UtbetalingUtbetalt(
            fødselsnummer = fnr,
            aktørId = fnr,
            organisasjonsnummer = org,
            fom = fom,
            tom = tom,
            utbetalingId = utbetalingId,
            antallVedtak = 1,
            event = "eventet",
            forbrukteSykedager = 42,
            gjenståendeSykedager = 3254,
            foreløpigBeregnetSluttPåSykepenger = null,
            automatiskBehandling = true,
            arbeidsgiverOppdrag = UtbetalingUtbetalt.OppdragDto(
                mottaker = org,
                fagområde = "SP",
                fagsystemId = "1234",
                nettoBeløp = 123,
                utbetalingslinjer = emptyList()
            ),
            type = "UTBETALING",
            utbetalingsdager = listOf(
                UtbetalingUtbetalt.UtbetalingdagDto(
                    dato = fom,
                    type = "AvvistDag",
                    begrunnelser = listOf(UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.MinimumSykdomsgrad)
                )
            )
        )
    }
}
