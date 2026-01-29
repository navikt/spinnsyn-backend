package no.nav.helse.flex.jobb

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.objectMapper
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be empty`
import org.amshove.kluent.`should not be null`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class MigrerTilUtbetalingsdagerJobbTest : FellesTestOppsett() {
    @Autowired
    private lateinit var jobb: MigrerTilUtbetalingsdagerJobb

    @BeforeEach
    fun setup() {
        vedtakRepository.deleteAll()
        utbetalingRepository.deleteAll()
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

        utbetalingRepository.hent500MedGammeltFormat().single().`should not be null`()
    }

    @Test
    fun `burde migrere utbetaling med gammelt format til nytt format`() {
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

        utbetalingRepository.hent500MedGammeltFormat().`should be empty`()

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
}
