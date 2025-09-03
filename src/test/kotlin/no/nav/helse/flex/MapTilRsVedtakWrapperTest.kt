package no.nav.helse.flex

import no.nav.helse.flex.db.Annullering
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.service.BrukerVedtak
import no.nav.helse.flex.service.IdentService
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MapTilRsVedtakWrapperTest {
    private val brukerVedtak =
        BrukerVedtak(
            identService = mock(IdentService::class.java),
            vedtakRepository = mock(),
            utbetalingRepository = mock(),
            annulleringDAO = mock(),
            leggTilOrganisasjonavn = mock(),
            vedtakStatusProducer = mock(),
        )

    @Test
    fun `mapTilRsVedtakWrapper håndterer SELVSTENDIG yrkesaktivitetstype korrekt`() {
        val vedtakFattet = lagVedtakFattetForEksternDto(yrkesaktivitetstype = "SELVSTENDIG")
        val utbetalingDbRecord = lagUtbetalingDbRecord()
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            brukerVedtak.mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.vedtak.yrkesaktivitetstype shouldBeEqualTo "SELVSTENDIG"
        resultat.vedtak.organisasjonsnummer shouldBeEqualTo vedtakFattet.organisasjonsnummer
        resultat.vedtak.fom shouldBeEqualTo vedtakFattet.fom
        resultat.vedtak.tom shouldBeEqualTo vedtakFattet.tom
        resultat.vedtak.sykepengegrunnlag shouldBeEqualTo vedtakFattet.sykepengegrunnlag
        resultat.vedtak.inntekt shouldBeEqualTo vedtakFattet.inntekt
    }

    @Test
    fun `mapTilRsVedtakWrapper bruker default ARBEIDSTAKER når yrkesaktivitetstype er null`() {
        val vedtakFattet = lagVedtakFattetForEksternDto(yrkesaktivitetstype = null)
        val utbetalingDbRecord = lagUtbetalingDbRecord()
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            brukerVedtak.mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.vedtak.yrkesaktivitetstype shouldBeEqualTo "ARBEIDSTAKER"
    }

    @Test
    fun `mapTilRsVedtakWrapper mapper alle felt korrekt fra VedtakFattetForEksternDto`() {
        val grunnlagPerArbeidsgiver = mapOf("123456789" to 500000.0, "987654321" to 300000.0)
        val vedtakFattet =
            lagVedtakFattetForEksternDto(
                organisasjonsnummer = "123456789",
                yrkesaktivitetstype = "ARBEIDSTAKER",
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 31),
                sykepengegrunnlag = 600000.0,
                inntekt = 50000.0,
                grunnlagForSykepengegrunnlag = 600000.0,
                grunnlagForSykepengegrunnlagPerArbeidsgiver = grunnlagPerArbeidsgiver,
                begrensning = "ER_6G_BEGRENSET",
                vedtakFattetTidspunkt = LocalDate.of(2024, 2, 1),
            )

        val utbetalingDbRecord =
            lagUtbetalingDbRecord(
                id = "test-id-123",
                opprettet = Instant.parse("2024-02-01T10:00:00Z"),
            )
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            brukerVedtak.mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.shouldNotBeNull()
        resultat.id shouldBeEqualTo "test-id-123"
        resultat.lest shouldBeEqualTo false
        resultat.orgnavn shouldBeEqualTo vedtakFattet.organisasjonsnummer
        resultat.andreArbeidsgivere shouldBeEqualTo grunnlagPerArbeidsgiver
        resultat.opprettetTimestamp shouldBeEqualTo utbetalingDbRecord.opprettet

        with(resultat.vedtak) {
            organisasjonsnummer shouldBeEqualTo "123456789"
            yrkesaktivitetstype shouldBeEqualTo "ARBEIDSTAKER"
            fom shouldBeEqualTo LocalDate.of(2024, 1, 1)
            tom shouldBeEqualTo LocalDate.of(2024, 1, 31)
            sykepengegrunnlag shouldBeEqualTo 600000.0
            inntekt shouldBeEqualTo 50000.0
            grunnlagForSykepengegrunnlag shouldBeEqualTo 600000.0
            grunnlagForSykepengegrunnlagPerArbeidsgiver shouldBeEqualTo grunnlagPerArbeidsgiver
            begrensning shouldBeEqualTo "ER_6G_BEGRENSET"
            vedtakFattetTidspunkt shouldBeEqualTo LocalDate.of(2024, 2, 1)
        }
    }

    @Test
    fun `mapTilRsVedtakWrapper håndterer lest dato korrekt`() {
        val lestTidspunkt = Instant.parse("2024-02-01T14:30:00Z")
        val vedtakFattet = lagVedtakFattetForEksternDto()
        val utbetalingDbRecord = lagUtbetalingDbRecord(lest = lestTidspunkt)
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            brukerVedtak.mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.lest shouldBeEqualTo true
        resultat.lestDato shouldBeEqualTo lestTidspunkt.atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
    }

    private fun lagVedtakFattetForEksternDto(
        organisasjonsnummer: String = "123456789",
        yrkesaktivitetstype: String? = "ARBEIDSTAKER",
        fom: LocalDate = LocalDate.now(),
        tom: LocalDate = LocalDate.now().plusDays(7),
        sykepengegrunnlag: Double = 500000.0,
        inntekt: Double = 45000.0,
        grunnlagForSykepengegrunnlag: Double = 500000.0,
        grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double> = mapOf("123456789" to 500000.0),
        begrensning: String = "VET_IKKE",
        vedtakFattetTidspunkt: LocalDate = LocalDate.now(),
    ) = VedtakFattetForEksternDto(
        fødselsnummer = "12345678901",
        aktørId = "1234567890123",
        organisasjonsnummer = organisasjonsnummer,
        yrkesaktivitetstype = yrkesaktivitetstype,
        fom = fom,
        tom = tom,
        skjæringstidspunkt = fom,
        dokumenter = emptyList(),
        inntekt = inntekt,
        sykepengegrunnlag = sykepengegrunnlag,
        grunnlagForSykepengegrunnlag = grunnlagForSykepengegrunnlag,
        grunnlagForSykepengegrunnlagPerArbeidsgiver = grunnlagForSykepengegrunnlagPerArbeidsgiver,
        begrensning = begrensning,
        utbetalingId = "test-utbetaling-123",
        vedtakFattetTidspunkt = vedtakFattetTidspunkt,
    )

    private fun lagUtbetalingDbRecord(
        id: String = "test-id-123",
        utbetalingId: String = "test-utbetaling-123",
        opprettet: Instant = Instant.now(),
        lest: Instant? = null,
    ) = UtbetalingDbRecord(
        id = id,
        utbetalingId = utbetalingId,
        fnr = "12345678901",
        utbetaling = lagUtbetalingUtbetalt().serialisertTilString(),
        opprettet = opprettet,
        utbetalingType = "UTBETALING",
        antallVedtak = 1,
        lest = lest,
        skalVisesTilBruker = true,
        motattPublisert = Instant.now(),
    )

    private fun lagVedtakDbRecord(
        vedtakFattet: VedtakFattetForEksternDto,
        utbetalingId: String,
    ) = VedtakDbRecord(
        id = "vedtak-123",
        vedtak = vedtakFattet.serialisertTilString(),
        fnr = vedtakFattet.fødselsnummer,
        utbetalingId = utbetalingId,
        opprettet = Instant.now(),
    )

    private fun lagUtbetalingUtbetalt() =
        UtbetalingUtbetalt(
            fødselsnummer = "12345678901",
            aktørId = "1234567890123",
            organisasjonsnummer = "123456789",
            fom = LocalDate.now(),
            tom = LocalDate.now().plusDays(7),
            utbetalingId = "test-utbetaling-123",
            antallVedtak = 1,
            event = "utbetaling_utbetalt",
            forbrukteSykedager = 10,
            gjenståendeSykedager = 238,
            foreløpigBeregnetSluttPåSykepenger = LocalDate.now().plusDays(238),
            automatiskBehandling = true,
            arbeidsgiverOppdrag =
                UtbetalingUtbetalt.OppdragDto(
                    mottaker = "123456789",
                    fagområde = "SPREF",
                    fagsystemId = "fagsystem-123",
                    nettoBeløp = 15000,
                    utbetalingslinjer = emptyList(),
                ),
            type = "UTBETALING",
            utbetalingsdager = emptyList(),
        )
}
