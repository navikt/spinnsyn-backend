package no.nav.helse.flex

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domene.*
import no.nav.helse.flex.domene.UtbetalingUtbetalt.UtbetalingdagDto
import no.nav.helse.flex.domene.UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.MinimumSykdomsgrad
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be false`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SkjonnsfastsettelseTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987123123"
    final val now = LocalDate.now()
    final val utbetalingId = "124542"
    val vedtak = VedtakFattetForEksternDto(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now,
        tom = now,
        skjæringstidspunkt = now,
        dokumenter = emptyList(),
        inntekt = 0.0,
        sykepengegrunnlag = 0.0,
        utbetalingId = utbetalingId,
        grunnlagForSykepengegrunnlag = 0.0,
        grunnlagForSykepengegrunnlagPerArbeidsgiver = mutableMapOf("1234" to 0.0),
        begrensning = "VET_IKKE",
        vedtakFattetTidspunkt = LocalDate.now(),
        sykepengegrunnlagsfakta = Sykepengegrunnlagsfakta.EtterSkjønn(
            fastsatt = "EtterSkjønn",
            skjønnsfastsatt = 12343.00,
            arbeidsgivere = emptyList(),
            omregnetÅrsinntekt = 123123.02,
            innrapportertÅrsinntekt = 900.0,
            avviksprosent = 27.0,
            `6G` = 668862.0,
            tags = emptyList()
        ).serialisertTilString().tilJsonNode(),
        begrunnelser = listOf(
            Begrunnelse(
                type = "SkjønnsfastsattSykepengegrunnlagFritekst",
                begrunnelse = "Begrunnelse fra saksbehandler",
                perioder = emptyList()
            ),
            Begrunnelse(
                type = "SkjønnsfastsattSykepengegrunnlagMal",
                begrunnelse = "Mal fra speil\nNy linje",
                perioder = emptyList()
            )
        )
    )

    val utbetaling = UtbetalingUtbetalt(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now,
        tom = now,
        utbetalingId = utbetalingId,
        antallVedtak = 1,
        event = "eventet",
        forbrukteSykedager = 42,
        gjenståendeSykedager = 3254,
        foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2020, 3, 12),
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
            UtbetalingdagDto(
                dato = now,
                type = "AvvistDag",
                begrunnelser = listOf(MinimumSykdomsgrad)
            )
        )
    )

    @Test
    @Order(1)
    fun `mottar vedtak`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        val hentetVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        hentetVedtak.vedtak.tilVedtakFattetForEksternDto().fødselsnummer.shouldBeEqualTo(fnr)
        hentetVedtak.utbetalingId.shouldBeEqualTo(vedtak.utbetalingId)
    }

    @Test
    @Order(2)
    fun `mottar utbetaling`() {
        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).isNotEmpty()
        }

        val dbUtbetaling = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first()
        dbUtbetaling.utbetaling.tilUtbetalingUtbetalt().fødselsnummer.shouldBeEqualTo(fnr)
        dbUtbetaling.utbetalingId.shouldBeEqualTo(utbetaling.utbetalingId)
        dbUtbetaling.utbetalingType.shouldBeEqualTo("UTBETALING")
    }

    @Test
    @Order(4)
    fun `finner vedtaket i apiet`() {
        val vedtak = hentVedtakMedTokenXToken(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].lest.`should be false`()
        vedtak[0].vedtak.begrunnelser!!.first().begrunnelse shouldBeEqualTo "Begrunnelse fra saksbehandler"
        vedtak[0].vedtak.begrunnelser!!.first().type shouldBeEqualTo "SkjønnsfastsattSykepengegrunnlagFritekst"

        val etterSkjønn = vedtak[0].vedtak.sykepengegrunnlagsfakta!!.tilEtterSkjønn()
        etterSkjønn.avviksprosent shouldBeEqualTo 27.0
        etterSkjønn.skjønnsfastsatt shouldBeEqualTo 12343.00
        etterSkjønn.arbeidsgivere.shouldBeEmpty()
        etterSkjønn.innrapportertÅrsinntekt shouldBeEqualTo 900.0
        etterSkjønn.omregnetÅrsinntekt shouldBeEqualTo 123123.02
        etterSkjønn.`6G` shouldBeEqualTo 668862.0
        etterSkjønn.tags.shouldBeEmpty()
    }
}

fun JsonNode.tilEtterSkjønn(): Sykepengegrunnlagsfakta.EtterSkjønn = objectMapper.readValue(this.serialisertTilString())

sealed class Sykepengegrunnlagsfakta {
    data class IInfotrygd(
        val fastsatt: String,
        val omregnetÅrsinntekt: Double
    ) : Sykepengegrunnlagsfakta()

    data class EtterHovedregel(
        val fastsatt: String,
        val arbeidsgivere: List<Arbeidsgiver>
    ) : Sykepengegrunnlagsfakta()

    data class EtterSkjønn(
        val fastsatt: String,
        val skjønnsfastsatt: Double,
        val arbeidsgivere: List<ArbeidsgiverMedSkjønn>,
        val omregnetÅrsinntekt: Double,
        val innrapportertÅrsinntekt: Double,
        val avviksprosent: Double,
        val `6G`: Double,
        val tags: List<String>
    ) : Sykepengegrunnlagsfakta()
}

data class Arbeidsgiver(
    val arbeidsgiver: String,
    val omregnetÅrsinntekt: Double
)

data class ArbeidsgiverMedSkjønn(
    val arbeidsgiver: String,
    val omregnetÅrsinntekt: Double,
    val skjønnsfastsatt: Double
)

fun String.tilJsonNode(): JsonNode {
    return OBJECT_MAPPER.readTree(this)
}
