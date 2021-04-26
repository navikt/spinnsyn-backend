package no.nav.helse.flex

import no.nav.helse.flex.vedtak.db.VedtakDbRecord
import no.nav.helse.flex.vedtak.db.VedtakRepository
import org.amshove.kluent.`should not be null`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant

class RepositoryTest : AbstractContainerBaseTest() {
    @Autowired
    lateinit var vedtakRepository: VedtakRepository

    @Test
    fun `Lagrer og oppdaterer vedtak`() {
        val vedtak = VedtakDbRecord(
            id = null,
            fnr = "827364",
            vedtak = "test data",
            opprettet = Instant.now(),
            lest = null
        )
        val save = vedtakRepository.save(vedtak)
        save.id.`should not be null`()

        vedtakRepository.save(save.copy(lest = Instant.now()))
        val hentet = vedtakRepository.findByIdOrNull(save.id!!)
        hentet!!.lest.`should not be null`()
    }
}
