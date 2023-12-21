package no.nav.helse.flex.organisasjon

import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component

@Component
class LeggTilOrganisasjonnavn(
    private val organisasjonRepository: OrganisasjonRepository,
) {
    val log = logger()

    fun leggTilOrganisasjonnavn(vedtakene: List<RSVedtakWrapper>): List<RSVedtakWrapper> {
        val orgnummerene =
            vedtakene
                .mapNotNull { it.vedtak.organisasjonsnummer }
                .toSet()

        val organisasjoner = assosierOrgNummerMedOrgNavn(orgnummerene)

        return vedtakene.map {
            val orgnavn = organisasjoner[it.vedtak.organisasjonsnummer]
            if (orgnavn != null) {
                it.copy(orgnavn = orgnavn)
            } else {
                it
            }
        }
    }

    fun leggTilAndreArbeidsgivere(vedtakene: List<RSVedtakWrapper>): List<RSVedtakWrapper> {
        val organisasjoner: Map<String, String> = assosierOrgNummerMedOrgNavn(vedtakene.orgNummere())

        return vedtakene.map {
            it.copy(
                andreArbeidsgivere =
                    it.vedtak.grunnlagForSykepengegrunnlagPerArbeidsgiver
                        ?.filterNot { organisasjon ->
                            it.vedtak.organisasjonsnummer == organisasjon.key
                        }
                        ?.leggTilAndreArbeidsgivere(organisasjoner),
                organisasjoner = organisasjoner,
            )
        }
    }

    private fun assosierOrgNummerMedOrgNavn(orgnummere: Set<String>) =
        organisasjonRepository.findByOrgnummerIn(orgnummere).associate { it.orgnummer to it.navn }
}

private fun List<RSVedtakWrapper>.orgNummere(): Set<String> =
    flatMap { it.vedtak.grunnlagForSykepengegrunnlagPerArbeidsgiver?.keys ?: emptySet() }
        .toSet()

private fun Map<String, Double>.leggTilAndreArbeidsgivere(organisasjoner: Map<String, String>) =
    mapKeys {
        organisasjoner[it.key] ?: "Organisasjonsnummer: ${it.key}"
    }
