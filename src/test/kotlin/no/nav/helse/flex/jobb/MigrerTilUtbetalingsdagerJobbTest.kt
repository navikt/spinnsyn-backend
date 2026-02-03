package no.nav.helse.flex.jobb

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.fake.EnvironmentTogglesFake
import no.nav.helse.flex.objectMapper
import org.amshove.kluent.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.UnexpectedRollbackException
import java.time.Instant
import java.util.*

class MigrerTilUtbetalingsdagerJobbTest : FellesTestOppsett() {
    @Autowired
    lateinit var environmentToggles: EnvironmentTogglesFake

    @Autowired
    private lateinit var jobb: MigrerTilUtbetalingsdagerJobb

    @BeforeEach
    fun setup() {
        vedtakRepository.deleteAll()
        utbetalingRepository.deleteAll()
        jobb.tilbakestill()
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
        environmentToggles.setEnvironment("dev")

        vedtakRepository.save(
            VedtakDbRecord(
                utbetalingId = "utbetaling-id",
                fnr = "12345678910",
                vedtak = VEDTAK_JSON,
                opprettet = Instant.parse("2021-01-01T12:00:00Z"),
            ),
        )
        val utbetaling =
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
        jobb.sistSettId `should be equal to` utbetaling.id

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

    @Test
    fun `burde sette sistSettId ved feil i batch, når utbetaling mangler vedtak`() {
        environmentToggles.setEnvironment("dev")

        val utbetaling =
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
        jobb.sistSettId `should be equal to` utbetaling.id
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

        utbetalingRepository.hent500MedGammeltFormat().size.`should be equal to`(500)
    }

    @Test
    fun `keyset med uuid-id burde gi stabil rekkefølge ved lik opprettet`() {
        val opprettet = Instant.parse("2021-01-01T12:00:00Z")

        utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = "12345678910",
                utbetalingType = "UTBETALING",
                utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                opprettet = opprettet,
                utbetalingId = "utbetaling-id-1",
                antallVedtak = 1,
            ),
        )

        utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = "12345678910",
                utbetalingType = "UTBETALING",
                utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                opprettet = opprettet,
                utbetalingId = "utbetaling-id-2",
                antallVedtak = 1,
            ),
        )

        val side1 = utbetalingRepository.hent500MedGammeltFormat()
        side1.size.`should be equal to`(2)

        val idForste = side1.first().id.`should not be null`()
        val idSiste = side1.last().id.`should not be null`()

        UUID.fromString(idForste)
        UUID.fromString(idSiste)

        val side2 = utbetalingRepository.hent500MedGammeltFormat(sistSettOpprettet = opprettet, sistSettId = idSiste)
        side2.`should be empty`()

        val side3 = utbetalingRepository.hent500MedGammeltFormat(sistSettOpprettet = opprettet, sistSettId = idForste)
        side3.size.`should be equal to`(1)
        side3.single().id.`should be equal to`(idSiste)
    }

    @Test
    fun `dry run utvalg burde være deterministisk i databasen`() {
        val opprettet = Instant.parse("2021-01-01T12:00:00Z")

        val lagredeUtbetalinger =
            (1..200).map { indeks ->
                utbetalingRepository.save(
                    UtbetalingDbRecord(
                        fnr = "12345678910",
                        utbetalingType = "UTBETALING",
                        utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                        opprettet = opprettet,
                        utbetalingId = "utbetaling-id-$indeks",
                        antallVedtak = 1,
                    ),
                )
            }

        val filtrert1 =
            utbetalingRepository.hent500MedGammeltFormat(
                sistSettOpprettet = null,
                sistSettId = null,
                andel = 10,
            )

        val filtrert2 =
            utbetalingRepository.hent500MedGammeltFormat(
                sistSettOpprettet = null,
                sistSettId = null,
                andel = 10,
            )

        val ider1 = filtrert1.mapNotNull { it.id }.toSet()
        val ider2 = filtrert2.mapNotNull { it.id }.toSet()

        ider1 `should be equal to` ider2

        // Tillater et intervall for antall resultater i stedet for eksakt antall
        filtrert1.size shouldBeInRange (5..30)
    }

    @Test
    fun `verifiserer at hashtext gir konsistent utvalg for samme UUID`() {
        val opprettet = Instant.parse("2021-01-01T12:00:00Z")

        val lagretUtbetaling =
            utbetalingRepository.save(
                UtbetalingDbRecord(
                    fnr = "12345678910",
                    utbetalingType = "UTBETALING",
                    utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                    opprettet = opprettet,
                    utbetalingId = "utbetaling-id-test",
                    antallVedtak = 1,
                ),
            )

        val id = lagretUtbetaling.id.`should not be null`()

        val resultat1 = utbetalingRepository.hent500MedGammeltFormat(andel = 10)
        val resultat2 = utbetalingRepository.hent500MedGammeltFormat(andel = 10)

        val inkludertIResultat1 = resultat1.any { it.id == id }
        val inkludertIResultat2 = resultat2.any { it.id == id }

        inkludertIResultat1.`should be equal to`(inkludertIResultat2)
    }
}
