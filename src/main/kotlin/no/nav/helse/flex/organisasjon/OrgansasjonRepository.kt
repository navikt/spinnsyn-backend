package no.nav.helse.flex.organisasjon

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OrganisasjonRepository : CrudRepository<Organisasjon, String> {
    fun findByOrgnummer(orgnummer: String): Organisasjon?
    fun findByOrgnummerIn(orgnummere: Set<String>): List<Organisasjon>
}

data class Organisasjon(
    @Id
    val id: String? = null,
    val orgnummer: String,
    val navn: String,
    val opprettet: Instant,
    val oppdatert: Instant,
    val oppdatertAv: String
)
