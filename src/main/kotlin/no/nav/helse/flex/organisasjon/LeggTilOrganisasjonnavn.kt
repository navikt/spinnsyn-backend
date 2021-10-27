package no.nav.helse.flex.organisasjon

import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component

@Component
class LeggTilOrganisasjonnavn(
    private val organisasjonRepository: OrganisasjonRepository
) {
    val log = logger()

    fun leggTilOrganisasjonnavn(vedtakene: List<RSVedtakWrapper>): List<RSVedtakWrapper> {

        val orgnummerene = vedtakene
            .mapNotNull { it.vedtak.organisasjonsnummer }
            .toSet()

        val orgnummerNavnMap = organisasjonRepository
            .findByOrgnummerIn(orgnummerene)
            .associate { it.orgnummer to it.navn }

        return vedtakene.map {
            if (orgnummerNavnMap.containsKey(it.vedtak.organisasjonsnummer)) {
                it.copy(orgnavn = orgnummerNavnMap[it.vedtak.organisasjonsnummer])
            } else {
                it
            }
        }
    }
}
