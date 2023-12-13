package no.nav.helse.flex.service

import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.domene.tilAnnulleringDto
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class MottaAnnulering(
    private val annulleringDAO: AnnulleringDAO,
    private val metrikk: Metrikk,
) {
    private val log = logger()

    fun mottaAnnullering(
        id: UUID,
        fnr: String,
        annullering: String,
        opprettet: Instant,
        kilde: String,
    ) {
        val annulleringSerialisert =
            try {
                annullering.tilAnnulleringDto()
            } catch (e: Exception) {
                throw RuntimeException("Kunne ikke deserialisere annulering", e)
            }

        annulleringDAO.finnAnnullering(fnr)
            .firstOrNull { it.annullering == annulleringSerialisert }
            ?.let {
                if (it.id == id.toString()) {
                    log.info("Annullering $id er allerede mottat, går videre")
                } else {
                    log.warn("Oppretter ikke duplikate annulleringer ny id: $id, eksisterende id: ${it.id}")
                }
                return
            }

        annulleringDAO.opprettAnnullering(
            id = id,
            fnr = fnr,
            annullering = annullering,
            opprettet = opprettet,
            kilde = kilde,
        )

        metrikk.mottattAnnulleringVedtakCounter.increment()

        log.info("Opprettet annullering med spinnsyn databaseid $id")
    }
}
