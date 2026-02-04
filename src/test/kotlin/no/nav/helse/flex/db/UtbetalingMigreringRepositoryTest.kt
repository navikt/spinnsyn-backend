package no.nav.helse.flex.db

import no.nav.helse.flex.FellesTestOppsett
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class UtbetalingMigreringRepositoryTest : FellesTestOppsett() {
    @Autowired
    lateinit var utbetalingMigreringRepository: UtbetalingMigreringRepository

    @Test
    fun `Kan lagre, hente og oppdatere UtbetalingMigreringDbRecord`() {
        utbetalingMigreringRepository.save(
            UtbetalingMigreringDbRecord(
                utbetalingId = "test-utbetaling-id",
                status = MigrertStatus.IKKE_MIGRERT,
            ),
        )

        val hentetRecord = utbetalingMigreringRepository.findByUtbetalingIdIn(listOf("test-utbetaling-id"))
        assert(hentetRecord.isNotEmpty())
        assert(hentetRecord.single().status == MigrertStatus.IKKE_MIGRERT)

        val recordToUpdate = hentetRecord.single().copy(status = MigrertStatus.MIGRERT)
        utbetalingMigreringRepository.save(recordToUpdate)
    }
}
