package no.nav.helse.flex.jobb

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.fake.EnvironmentTogglesFake
import no.nav.helse.flex.objectMapper
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be empty`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.`should throw`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.UnexpectedRollbackException
import java.time.Instant

class MigrerTilUtbetalingsdagerJobbTest : FellesTestOppsett() {
    @Autowired
    lateinit var environmentToggles: EnvironmentTogglesFake

    @Autowired
    private lateinit var jobb: MigrerTilUtbetalingsdagerJobb

    @BeforeEach
    fun setup() {
        vedtakRepository.deleteAll()
        utbetalingRepository.deleteAll()
        jobb.offset.set(0)
    }

    @Test
    fun `burde hente ut utbetalinger som skal migreres`() {
        utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = "12345678910",
                utbetalingType = "UTBETALING",
                utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                opprettet = Instant.parse("2021-01-01T12:00:00Z"),
                utbetalingId = "utbetaling-id",
                antallVedtak = 1,
            ),
        )

        utbetalingRepository.hent500MedGammeltFormatMedOffset().single().`should not be null`()
    }

    @Test
    fun `burde migrere utbetaling med gammelt format til nytt format`() {
        environmentToggles.setEnvironment("dev")

        vedtakRepository.save(
            VedtakDbRecord(
                utbetalingId = "utbetaling-id",
                fnr = "12345678910",
                vedtak = VEDTAK_JSON,
                opprettet = Instant.parse("2021-01-01T12:00:00Z"),
            ),
        )
        utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = "12345678910",
                utbetalingType = "UTBETALING",
                utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                opprettet = Instant.now(),
                utbetalingId = "utbetaling-id",
                antallVedtak = 1,
                lest = Instant.parse("2024-01-01T00:00:00Z"),
                motattPublisert = Instant.parse("2023-01-01T00:00:00Z"),
                skalVisesTilBruker = true,
            ),
        )
        jobb.kjørMigreringTilUtbetalingsdager()

        utbetalingRepository.hent500MedGammeltFormatMedOffset().`should be empty`()

        utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr = "12345678910").single().also { utbetalingDbRecord ->
            objectMapper
                .readValue<UtbetalingUtbetalt>(utbetalingDbRecord.utbetaling)
                .also { utbetalingUtbetalt ->
                    utbetalingUtbetalt.`should not be null`()
                    utbetalingUtbetalt.utbetalingsdager
                        .`should not be empty`()
                        .forEach {
                            when (it.type) {
                                "NavDag" -> {
                                    it.sykdomsgrad.`should be equal to`(100)
                                    it.begrunnelser.`should be empty`()
                                    it.beløpTilSykmeldt.`should not be null`()
                                    it.beløpTilArbeidsgiver.`should be equal to`(2077)
                                }

                                "NavHelgDag" -> {
                                    it.sykdomsgrad.`should be equal to`(0)
                                    it.begrunnelser.`should be empty`()
                                    it.beløpTilSykmeldt.`should be equal to`(0)
                                    it.beløpTilArbeidsgiver.`should be equal to`(0)
                                }

                                "AvvistDag" -> {
                                    it.sykdomsgrad.`should be equal to`(0)
                                    it.begrunnelser.`should not be empty`()
                                    it.begrunnelser.first().`should be equal to`(
                                        UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.MinimumInntektOver67,
                                    )
                                }
                            }
                        }
                }
        }
    }

    @Disabled("Ikke gjeldende for dry run")
    @Test
    fun `burde øke offset med antall feil i batch, når utbetaling mangler vedtak`() {
        environmentToggles.setEnvironment("dev")

        utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = "12345678910",
                utbetalingType = "UTBETALING",
                utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                opprettet = Instant.now(),
                utbetalingId = "utbetaling-id",
                antallVedtak = 1,
            ),
        )

        jobb.kjørMigreringTilUtbetalingsdager()
        val offset = jobb.offset.get()
        offset `should be equal to` 1
    }

    @Test
    fun `dry run burde ikke endre utbetalinger`() {
        environmentToggles.setEnvironment("prod")

        val fnr = "12345678910"

        (1..500).forEach { indeks ->
            val utbetalingId = "utbetaling-id-$indeks"

            vedtakRepository.save(
                VedtakDbRecord(
                    utbetalingId = utbetalingId,
                    fnr = fnr,
                    vedtak =
                        VEDTAK_JSON.replace(
                            "\"utbetalingId\":\"3d19a9d1-c285-4dcd-abb3-b3bcfb538c4a\"",
                            "\"utbetalingId\":\"$utbetalingId\"",
                        ),
                    opprettet = Instant.parse("2021-01-01T12:00:00Z"),
                ),
            )

            utbetalingRepository.save(
                UtbetalingDbRecord(
                    fnr = fnr,
                    utbetalingType = "UTBETALING",
                    utbetaling =
                        UTBETALING_GAMMELT_FORMAT_JSON.replace(
                            "\"utbetalingId\":\"3d19a9d1-c285-4dcd-abb3-b3bcfb538c4a\"",
                            "\"utbetalingId\":\"$utbetalingId\"",
                        ),
                    opprettet = Instant.parse("2021-01-01T12:00:00Z"),
                    utbetalingId = utbetalingId,
                    antallVedtak = 1,
                ),
            )
        }

        val utbetalingerFør = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        utbetalingerFør.size.`should be equal to`(500)
        utbetalingerFør.all { it.utbetaling.contains("\"sykdomsgrad\"") }.`should be equal to`(false)

        invoking {
            jobb.kjørMigreringTilUtbetalingsdager()
        } `should throw` UnexpectedRollbackException::class

        val utbetalingerEtterpå = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        utbetalingerEtterpå.size.`should be equal to`(500)

        val harBlittMigrert =
            utbetalingerEtterpå.any { utbetalingDbRecord ->
                objectMapper.readValue<UtbetalingUtbetalt>(utbetalingDbRecord.utbetaling).utbetalingsdager.any { it.sykdomsgrad != null }
            }

        harBlittMigrert.`should be equal to`(false)

        utbetalingRepository.hent500MedGammeltFormatMedOffset().size.`should be equal to`(500)
    }
}
