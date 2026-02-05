package no.nav.helse.flex.jobb

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.db.MigrertStatus
import no.nav.helse.flex.db.UtbetalingMigreringRepository
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.fake.EnvironmentTogglesFake
import no.nav.helse.flex.jobb.MigreringsTestData.UTBETALING_GAMMELT_FORMAT_JSON
import no.nav.helse.flex.jobb.MigreringsTestData.UTBETALING_UTEN_UTBETALINGSDAGER_JSON
import no.nav.helse.flex.objectMapper
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.`should not be null`
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class MigrerTilUtbetalingsdagerBatchMigratorTest : FellesTestOppsett() {
    @Autowired
    private lateinit var batchMigrator: MigrerTilUtbetalingsdagerBatchMigrator

    @Autowired
    private lateinit var utbetalingMigreringRepository: UtbetalingMigreringRepository

    @Autowired
    private lateinit var environmentToggles: EnvironmentTogglesFake

    @BeforeEach
    fun setup() {
        vedtakRepository.deleteAll()
        utbetalingRepository.deleteAll()
        utbetalingMigreringRepository.deleteAll()
    }

    @BeforeAll
    fun brukDevPgaDryRun() {
        environmentToggles.setEnvironment("dev")
    }

    @Test
    fun `Migrerer utbetaling med gammelt format til nytt format`() {
        val utbetalingId = "utbetaling-id"
        val fnr = "12345678910"

        val vedtak = vedtakRepository.opprettVedtak(utbetalingId, fnr)
        val utbetaling = utbetalingRepository.opprettUtbetaling(utbetalingId, fnr, UTBETALING_GAMMELT_FORMAT_JSON)
        utbetalingMigreringRepository.opprettMigreringsRecord(utbetalingId, MigrertStatus.IKKE_MIGRERT)

        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(utbetaling to listOf(vedtak)))

        resultat.migrert.`should be equal to`(1)
        resultat.feilet.`should be equal to`(0)
    }

    @Test
    fun `Migrering feiler når utbetaling ikke har tilhørende vedtak`() {
        val utbetalingId = "mangler-vedtak"
        val utbetaling =
            utbetalingRepository.opprettUtbetaling(
                utbetalingId,
                "12345678910",
                UTBETALING_GAMMELT_FORMAT_JSON,
                lagreIDb = false,
            )
        utbetalingMigreringRepository.opprettMigreringsRecord(utbetalingId, MigrertStatus.IKKE_MIGRERT)

        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(utbetaling to emptyList()))

        resultat.migrert.`should be equal to`(0)
        resultat.feilet.`should be equal to`(1)

        utbetalingMigreringRepository.verifiserMigreringsStatus(utbetalingId, MigrertStatus.FEILET)
    }

    @Test
    fun `Migrering feiler når utbetaling har ugyldig json`() {
        val utbetalingId = "ugyldig-json-id"
        val vedtak = vedtakRepository.opprettVedtak(utbetalingId, "12345678910", lagreIDb = false)
        val utbetaling = utbetalingRepository.opprettUtbetaling(utbetalingId, "12345678910", "{ikke gyldig json", lagreIDb = false)
        utbetalingMigreringRepository.opprettMigreringsRecord(utbetalingId, MigrertStatus.IKKE_MIGRERT)

        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(utbetaling to listOf(vedtak)))

        resultat.migrert.`should be equal to`(0)
        resultat.feilet.`should be equal to`(1)

        utbetalingMigreringRepository.verifiserMigreringsStatus(utbetalingId, MigrertStatus.FEILET)
    }

    @Test
    fun `Migrering feiler når utbetaling mangler utbetalingsdager`() {
        val utbetalingId = "uten-dager"
        val fnr = "12345678910"

        val vedtak = vedtakRepository.opprettVedtak(utbetalingId, fnr, lagreIDb = false)
        val utbetaling = utbetalingRepository.opprettUtbetaling(utbetalingId, fnr, UTBETALING_UTEN_UTBETALINGSDAGER_JSON)
        utbetalingMigreringRepository.opprettMigreringsRecord(utbetalingId, MigrertStatus.IKKE_MIGRERT)

        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(utbetaling to listOf(vedtak)))

        resultat.migrert.`should be equal to`(0)
        resultat.feilet.`should be equal to`(1)

        utbetalingRepository
            .findById(utbetaling.id!!)
            .also {
                it.isPresent.`should be true`()
                it
                    .get()
                    .utbetaling
                    .contains("\"utbetalingsdager\"")
                    .`should be equal to`(false)
            }

        utbetalingMigreringRepository.verifiserMigreringsStatus(utbetalingId, MigrertStatus.FEILET)
    }

    @Test
    fun `Burde håndtere flere vedtak for en utbetaling`() {
        val utbetalingId = "utbetaling-med-flere-vedtak"
        val fnr = "12345678910"

        val vedtak1 = vedtakRepository.opprettVedtak(utbetalingId, fnr)
        val vedtak2 = vedtakRepository.opprettVedtak(utbetalingId, fnr, opprettet = Instant.parse("2021-01-02T12:00:00Z"))
        val utbetaling = utbetalingRepository.opprettUtbetaling(utbetalingId, fnr, UTBETALING_GAMMELT_FORMAT_JSON, antallVedtak = 2)
        utbetalingMigreringRepository.opprettMigreringsRecord(utbetalingId, MigrertStatus.IKKE_MIGRERT)

        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(utbetaling to listOf(vedtak1, vedtak2)))

        resultat.migrert.`should be equal to`(1)
        resultat.feilet.`should be equal to`(0)
        utbetalingMigreringRepository.verifiserMigreringsStatus(utbetalingId, MigrertStatus.MIGRERT)

        utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr = fnr).single().also { utbetalingDbRecord ->
            val utbetalingUtbetalt = objectMapper.readValue<UtbetalingUtbetalt>(utbetalingDbRecord.utbetaling)

            utbetalingUtbetalt.utbetalingsdager.`should not be null`()
            utbetalingUtbetalt.utbetalingsdager.size `should be equal to` 31
            utbetalingUtbetalt.utbetalingsdager.count { it.type == "NavDag" } `should be equal to` 22
            utbetalingUtbetalt.utbetalingsdager.count { it.type == "NavHelgDag" } `should be equal to` 8
            utbetalingUtbetalt.utbetalingsdager.count { it.type == "AvvistDag" } `should be equal to` 1
        }
    }
}
