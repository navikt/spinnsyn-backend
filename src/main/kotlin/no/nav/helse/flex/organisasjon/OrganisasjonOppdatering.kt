package no.nav.helse.flex.organisasjon

import no.nav.helse.flex.logger
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OrganisasjonOppdatering(
    private val organisasjonRepository: OrganisasjonRepository
) {
    val log = logger()

    fun handterSoknad(soknad: SykepengesoknadDTO) {
        if (soknad.harArbeidsgiver()) {
            val orgnummer = soknad.arbeidsgiver!!.orgnummer!!
            val navn = soknad.arbeidsgiver!!.navn!!
            val eksisterende = organisasjonRepository.findByOrgnummer(orgnummer)

            if (eksisterende == null) {
                organisasjonRepository.save(
                    Organisasjon(
                        orgnummer = orgnummer,
                        navn = navn,
                        oppdatert = Instant.now(),
                        opprettet = Instant.now(),
                        oppdatertAv = soknad.id,
                    )
                )
                return
            } else {
                if (eksisterende.navn != navn) {
                    log.info("Endrer navn på $orgnummer fra ${eksisterende.navn} til $navn. Opprinnelig kilde ${eksisterende.oppdatertAv}. Ny kilde ${soknad.id}")
                    organisasjonRepository.save(
                        eksisterende.copy(
                            navn = navn,
                            oppdatertAv = soknad.id,
                            oppdatert = Instant.now(),
                        )
                    )
                }
            }
        }
    }
}

private fun SykepengesoknadDTO.harArbeidsgiver(): Boolean {
    return this.arbeidsgiver?.orgnummer?.isNotBlank() ?: false && this.arbeidsgiver?.navn?.isNotBlank() ?: false
}
