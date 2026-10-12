package no.nav.helse.flex.domene

import no.nav.helse.flex.objectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

data class AnnulleringDto(
    val orgnummer: String?,
    val organisasjonsnummer: String?,
    val fødselsnummer: String,
    val fom: LocalDate?,
    val tom: LocalDate?,
)

fun String.tilAnnulleringDto(): AnnulleringDto = objectMapper.readValue(this)
