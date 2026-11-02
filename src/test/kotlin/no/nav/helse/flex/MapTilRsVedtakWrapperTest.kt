package no.nav.helse.flex

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.db.Annullering
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.domene.Forsikringsvurdering
import no.nav.helse.flex.domene.Saksbehandler
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.service.BrukerVedtak.Companion.mapTilRsVedtakWrapper
import no.nav.helse.flex.testdata.lagArbeidsgiverOppdrag
import no.nav.helse.flex.testdata.lagForsikringsvurdering
import no.nav.helse.flex.testdata.lagUtbetaling
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MapTilRsVedtakWrapperTest {
    @Test
    fun `mapTilRsVedtakWrapper håndterer SELVSTENDIG yrkesaktivitetstype korrekt`() {
        val vedtakFattet = lagVedtakFattetForEksternDto(yrkesaktivitetstype = "SELVSTENDIG")
        val utbetalingDbRecord = lagUtbetalingDbRecord()
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.vedtak.yrkesaktivitetstype shouldBe "SELVSTENDIG"
        resultat.vedtak.organisasjonsnummer shouldBe vedtakFattet.organisasjonsnummer
        resultat.vedtak.fom shouldBe vedtakFattet.fom
        resultat.vedtak.tom shouldBe vedtakFattet.tom
        resultat.vedtak.sykepengegrunnlag shouldBe vedtakFattet.sykepengegrunnlag
        resultat.vedtak.inntekt shouldBe vedtakFattet.inntekt
    }

    @Test
    fun `mapTilRsVedtakWrapper bruker default ARBEIDSTAKER når yrkesaktivitetstype er null`() {
        val vedtakFattet = lagVedtakFattetForEksternDto(yrkesaktivitetstype = null)
        val utbetalingDbRecord = lagUtbetalingDbRecord()
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.vedtak.yrkesaktivitetstype shouldBe "ARBEIDSTAKER"
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
                forsikringsvurdering = lagForsikringsvurdering(),
            )

        val utbetalingDbRecord =
            lagUtbetalingDbRecord(
                id = "test-id-123",
                opprettet = Instant.parse("2024-02-01T10:00:00Z"),
            )
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.shouldNotBeNull()
        resultat.id shouldBe "test-id-123"
        resultat.lest shouldBe false
        resultat.orgnavn shouldBe vedtakFattet.organisasjonsnummer
        resultat.andreArbeidsgivere shouldBe grunnlagPerArbeidsgiver
        resultat.opprettetTimestamp shouldBe utbetalingDbRecord.opprettet

        with(resultat.vedtak) {
            organisasjonsnummer shouldBe "123456789"
            yrkesaktivitetstype shouldBe "ARBEIDSTAKER"
            fom shouldBe LocalDate.of(2024, 1, 1)
            tom shouldBe LocalDate.of(2024, 1, 31)
            sykepengegrunnlag shouldBe 600000.0
            inntekt shouldBe 50000.0
            grunnlagForSykepengegrunnlag shouldBe 600000.0
            grunnlagForSykepengegrunnlagPerArbeidsgiver shouldBe grunnlagPerArbeidsgiver
            begrensning shouldBe "ER_6G_BEGRENSET"
            vedtakFattetTidspunkt shouldBe LocalDate.of(2024, 2, 1)
            saksbehandler.shouldNotBeNull().let {
                it.navn shouldBe "Saksbehandler"
                it.ident shouldBe "ident-saksbehandler"
            }
            beslutter.shouldNotBeNull().let {
                it.navn shouldBe "Beslutter"
                it.ident shouldBe "ident-beslutter"
            }
            forsikringsvurdering.shouldNotBeNull().let {
                it.forsikringsvurderingId shouldBe "forsikringsvurdering-123"
                it.individuellForsikringNavn shouldBe "Individuell Forsikring AS"
                it.kollektivForsikringNavn shouldBe "Kollektiv Forsikring AS"
                it.dekning.shouldNotBeNull().let { dekning ->
                    dekning.grad shouldBe 80
                    dekning.fraDag shouldBe 1
                }
            }
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
            mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.lest shouldBe true
        resultat.lestDato shouldBe lestTidspunkt.atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
    }

    @Test
    fun `mapTilRsVedtakWrapper håndterer gammel utbetaling uten nye felter i utbetalingsdager`() {
        // Simulerer JSON fra database før nye felter ble lagt til
        val gammelUtbetalingJson =
            """
            {
              "event": "utbetaling_utbetalt",
              "utbetalingId": "test-utbetaling-123",
              "fødselsnummer": "12345678901",
              "aktørId": "1234567890123",
              "organisasjonsnummer": "123456789",
              "fom": "2024-01-01",
              "tom": "2024-01-31",
              "forbrukteSykedager": 10,
              "gjenståendeSykedager": 238,
              "automatiskBehandling": true,
              "type": "UTBETALING",
              "antallVedtak": 1,
              "foreløpigBeregnetSluttPåSykepenger": "2024-12-31",
              "utbetalingsdager": [
                {
                  "dato": "2024-01-17",
                  "type": "NavDag",
                  "begrunnelser": []
                },
                {
                  "dato": "2024-01-18",
                  "type": "NavDag",
                  "begrunnelser": []
                }
              ]
            }
            """.trimIndent()

        val vedtakFattet = lagVedtakFattetForEksternDto()
        val utbetalingDbRecord =
            lagUtbetalingDbRecord(
                utbetalingJson = gammelUtbetalingJson,
            )
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.shouldNotBeNull()
        val utbetalingsdager = resultat.vedtak.utbetaling.utbetalingsdager
        utbetalingsdager.shouldNotBeNull()
        utbetalingsdager.size shouldBe 2

        // Gamle utbetalinger skal ha null for de nye feltene
        utbetalingsdager[0].beløpTilArbeidsgiver shouldBe null
        utbetalingsdager[0].beløpTilSykmeldt shouldBe null
        utbetalingsdager[0].sykdomsgrad shouldBe null
    }

    @Test
    fun `mapTilRsVedtakWrapper håndterer ny utbetaling med nye felter i utbetalingsdager`() {
        // Simulerer JSON fra database med nye felter
        val nyUtbetalingJson =
            """
            {
              "event": "utbetaling_utbetalt",
              "utbetalingId": "test-utbetaling-123",
              "fødselsnummer": "12345678901",
              "aktørId": "1234567890123",
              "organisasjonsnummer": "123456789",
              "fom": "2024-01-01",
              "tom": "2024-01-31",
              "forbrukteSykedager": 10,
              "gjenståendeSykedager": 238,
              "automatiskBehandling": true,
              "type": "UTBETALING",
              "antallVedtak": 1,
              "foreløpigBeregnetSluttPåSykepenger": "2024-12-31",
              "utbetalingsdager": [
                {
                  "dato": "2024-01-17",
                  "type": "NavDag",
                  "begrunnelser": [],
                  "beløpTilArbeidsgiver": 1000,
                  "beløpTilSykmeldt": 500,
                  "sykdomsgrad": 100
                },
                {
                  "dato": "2024-01-18",
                  "type": "NavDag",
                  "begrunnelser": [],
                  "beløpTilArbeidsgiver": 800,
                  "beløpTilSykmeldt": 200,
                  "sykdomsgrad": 75
                }
              ]
            }
            """.trimIndent()

        val vedtakFattet = lagVedtakFattetForEksternDto()
        val utbetalingDbRecord =
            lagUtbetalingDbRecord(
                utbetalingJson = nyUtbetalingJson,
            )
        val vedtakDbRecord = lagVedtakDbRecord(vedtakFattet, utbetalingDbRecord.utbetalingId)
        val annulleringer = emptyList<Annullering>()

        val resultat =
            mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetalingDbRecord,
                vedtakMedUtbetaling = listOf(vedtakDbRecord),
                annulleringer = annulleringer,
            )

        resultat.shouldNotBeNull()
        val utbetalingsdager = resultat.vedtak.utbetaling.utbetalingsdager
        utbetalingsdager.shouldNotBeNull()
        utbetalingsdager.size shouldBe 2

        utbetalingsdager[0].beløpTilArbeidsgiver shouldBe 1000
        utbetalingsdager[0].beløpTilSykmeldt shouldBe 500
        utbetalingsdager[0].sykdomsgrad shouldBe 100

        utbetalingsdager[1].beløpTilArbeidsgiver shouldBe 800
        utbetalingsdager[1].beløpTilSykmeldt shouldBe 200
        utbetalingsdager[1].sykdomsgrad shouldBe 75
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
        forsikringsvurdering: Forsikringsvurdering? = null,
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
        saksbehandler =
            Saksbehandler(
                navn = "Saksbehandler",
                ident = "ident-saksbehandler",
            ),
        beslutter =
            Saksbehandler(
                navn = "Beslutter",
                ident = "ident-beslutter",
            ),
        forsikringsvurdering = forsikringsvurdering,
    )

    private fun lagUtbetalingDbRecord(
        id: String = "test-id-123",
        utbetalingId: String = "test-utbetaling-123",
        opprettet: Instant = Instant.now(),
        lest: Instant? = null,
        utbetalingJson: String? = null,
    ) = UtbetalingDbRecord(
        id = id,
        utbetalingId = utbetalingId,
        fnr = "12345678901",
        utbetaling = utbetalingJson ?: lagUtbetalingUtbetalt().serialisertTilString(),
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
        lagUtbetaling(
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
            arbeidsgiverOppdrag =
                lagArbeidsgiverOppdrag(
                    mottaker = "123456789",
                    fagområde = "SPREF",
                    fagsystemId = "fagsystem-123",
                    nettoBeløp = 15000,
                ),
            type = "UTBETALING",
            utbetalingsdager = emptyList(),
        )
}
