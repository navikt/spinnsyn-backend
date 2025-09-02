package no.nav.helse.flex

import no.nav.helse.flex.organisasjon.LeggTilOrganisasjonnavn
import no.nav.helse.flex.organisasjon.Organisasjon
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class ErstattOrgNrMedOrgNavnTest : FellesTestOppsett() {
    @Autowired
    lateinit var leggTilOrganisasjonnavn: LeggTilOrganisasjonnavn

    @Test
    fun `Erstatter orgNr med OrgNavn`() {
        organisasjonRepository.deleteAll()

        val org1 =
            Organisasjon(
                orgnummer = "123456547",
                navn = "Organisasjon nr 1",
                id = null,
                oppdatert = Instant.now(),
                oppdatertAv = "",
                opprettet = Instant.now(),
            )

        organisasjonRepository.saveAll(mutableListOf(org1))

        val orgMap = leggTilOrganisasjonnavn.leggTilAndreArbeidsgivere(vedtakene = listOf(vedtakTestdata()))
        orgMap[0].andreArbeidsgivere `should be equal to`
            mapOf(
                "Organisasjon nr 1" to 500000.0,
                "Organisasjonsnummer: 547123456" to 300000.0,
            )

        orgMap[0].organisasjoner `should be equal to`
            mapOf(
                "123456547" to "Organisasjon nr 1",
            )
    }
}
