package no.nav.helse.flex.organisasjon

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.data.relational.core.conversion.DbActionExecutionException
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OrganisasjonOppdatering(
    private val organisasjonRepository: OrganisasjonRepository
) {
    val log = logger()

    fun handterSoknad(soknad: SykepengesoknadDTO) {
        if (soknad.harArbeidsgiver() && soknad.erNy()) {
            val orgnummer = soknad.arbeidsgiver!!.orgnummer!!
            val navn = soknad.arbeidsgiver!!.navn!!
            val eksisterende = organisasjonRepository.findByOrgnummer(orgnummer)

            if (eksisterende == null) {
                log.info("Lagrer ny organisasjon med orgnummer: $orgnummer og navn: $navn.")
                lagreOrganisasjon(
                    Organisasjon(
                        orgnummer = orgnummer,
                        navn = navn,
                        oppdatert = Instant.now(),
                        opprettet = Instant.now(),
                        oppdatertAv = soknad.id,
                    )
                )
            } else {
                if (eksisterende.navn != navn) {
                    log.info(
                        "Endrer navn på organisasjon med orgnummer: $orgnummer fra: ${eksisterende.navn} til: $navn. " +
                            "Opprinnelig kilde: ${eksisterende.oppdatertAv}. Ny kilde: ${soknad.id}"
                    )
                    lagreOrganisasjon(
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

    private fun lagreOrganisasjon(organisasjon: Organisasjon) {
        // Hvis det kommer to samtidige meldinger om den samme organisasjonen som blir prosessert på to forskjellige
        // instanser kan det resultere i DuplicateKeyException fra databasen.
        try {
            organisasjonRepository.save(organisasjon)
        } catch (e: DbActionExecutionException) {
            log.warn("Feil ved lagring av organisasjon med orgnummer: ${organisasjon.orgnummer} til database.", e)
        }
    }
}

private fun SykepengesoknadDTO.harArbeidsgiver(): Boolean {
    return this.arbeidsgiver?.orgnummer?.isNotBlank() ?: false && this.arbeidsgiver?.navn?.isNotBlank() ?: false
}

private fun SykepengesoknadDTO.erNy(): Boolean {
    return this.status == SoknadsstatusDTO.NY
}
