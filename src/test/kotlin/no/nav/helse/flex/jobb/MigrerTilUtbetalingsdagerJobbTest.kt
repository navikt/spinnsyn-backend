package no.nav.helse.flex.jobb

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.db.MigrertStatus
import no.nav.helse.flex.db.UtbetalingMigreringRepository
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.fake.EnvironmentTogglesFake
import no.nav.helse.flex.objectMapper
import org.amshove.kluent.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.UnexpectedRollbackException
import java.time.Instant

class MigrerTilUtbetalingsdagerJobbTest : FellesTestOppsett() {
    @Autowired
    lateinit var environmentToggles: EnvironmentTogglesFake

    @Autowired
    private lateinit var jobb: MigrerTilUtbetalingsdagerJobb

    @Autowired
    private lateinit var utbetalingMigreringRepository: UtbetalingMigreringRepository

    @BeforeEach
    fun setup() {
        vedtakRepository.deleteAll()
        utbetalingRepository.deleteAll()
        utbetalingMigreringRepository.deleteAll()
    }

    @Test
    fun `Burde hente ut utbetalinger med gammelt format`() {
        utbetalingRepository.opprettUtbetaling("utbetaling-id", "12345678910")

        utbetalingRepository.hent500MedGammeltFormat().single().`should not be null`()
    }

    @Test
    fun `Burde migrere utbetaling med gammelt format til nytt format`() {
        environmentToggles.setEnvironment("dev")
        val utbetalingId = "utbetaling-id"
        val fnr = "12345678910"

        vedtakRepository.opprettVedtak(utbetalingId, fnr)
        utbetalingRepository.opprettUtbetaling(
            utbetalingId,
            fnr,
            lest = Instant.parse("2024-01-01T00:00:00Z"),
            motattPublisert = Instant.parse("2023-01-01T00:00:00Z"),
            skalVisesTilBruker = true,
        )
        utbetalingMigreringRepository.opprettMigreringsRecord(utbetalingId, MigrertStatus.IKKE_MIGRERT)

        jobb.kjørMigreringTilUtbetalingsdager()

        utbetalingMigreringRepository.verifiserMigreringsStatus(utbetalingId, MigrertStatus.MIGRERT)
        utbetalingMigreringRepository.findFirst500ByStatus(MigrertStatus.IKKE_MIGRERT).`should be empty`()

        verifiserUtbetalingMigrert(fnr)
    }

    @Test
    fun `Burde ikke kaste feil når migrering feiler (mangler vedtak)`() {
        environmentToggles.setEnvironment("dev")
        val utbetalingId = "utbetaling-id"

        utbetalingRepository.opprettUtbetaling(utbetalingId, "12345678910")
        utbetalingMigreringRepository.opprettMigreringsRecord(utbetalingId, MigrertStatus.IKKE_MIGRERT)

        invoking {
            jobb.kjørMigreringTilUtbetalingsdager()
        } `should not throw` Exception::class

        utbetalingMigreringRepository.verifiserMigreringsStatus(utbetalingId, MigrertStatus.FEILET)
    }

    @Test
    fun `dry run burde ikke endre utbetalinger`() {
        environmentToggles.setEnvironment("prod")
        val utbetalingId = "utbetaling-id"
        val fnr = "12345678910"

        vedtakRepository.opprettVedtak(utbetalingId, fnr)
        utbetalingRepository.opprettUtbetaling(utbetalingId, fnr)
        utbetalingMigreringRepository.opprettMigreringsRecord(utbetalingId, MigrertStatus.IKKE_MIGRERT)

        val utbetalingerFør = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        utbetalingerFør.`should not be empty`()
        utbetalingerFør
            .single()
            .utbetaling
            .contains("\"sykdomsgrad\"")
            .`should be false`()

        invoking {
            jobb.kjørMigreringTilUtbetalingsdager()
        } `should throw` UnexpectedRollbackException::class

        val utbetalingerEtterJobb = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        utbetalingerEtterJobb.shouldHaveSize(1)

        val harBlittMigrert =
            utbetalingerEtterJobb.any { utbetalingDbRecord ->
                objectMapper.readValue<UtbetalingUtbetalt>(utbetalingDbRecord.utbetaling).utbetalingsdager.any { it.sykdomsgrad != null }
            }

        harBlittMigrert.`should be equal to`(false)
        utbetalingRepository.hent500MedGammeltFormat().shouldHaveSize(1)
        utbetalingMigreringRepository.findFirst500ByStatus(MigrertStatus.MIGRERT).`should be empty`()
    }

    private fun verifiserUtbetalingMigrert(fnr: String) {
        utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr = fnr).single().also { utbetalingDbRecord ->
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
