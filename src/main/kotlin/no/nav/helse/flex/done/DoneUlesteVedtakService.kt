package no.nav.helse.flex.done

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DoneUlesteVedtakService(
    private val doneUlesteVedtakRepository: DoneUlesteVedtakRepository,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
    @Value("\${on-prem-kafka.username}")
    private val serviceuserUsername: String,
) {

    val log = logger()

    fun doneUlesteVedtak(batchSize: Int) {
        val ulesteVedtak = doneUlesteVedtakRepository.hentUleste(batchSize)
        ulesteVedtak.forEach { sendDoneMelding(fnr = it.fnr, id = it.id) }

        log.info("Sendt ${ulesteVedtak.size} done-meldinger.")
        doneUlesteVedtakRepository.settDoneTidspunkt(ulesteVedtak.map { it.id }.toList())
    }

    private fun sendDoneMelding(fnr: String, id: String) {
        brukernotifikasjonKafkaProdusent.sendDonemelding(
            Nokkel(serviceuserUsername, id),
            Done(Instant.now().toEpochMilli(), fnr, id)
        )
    }
}
