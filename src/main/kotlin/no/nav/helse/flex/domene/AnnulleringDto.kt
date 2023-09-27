package no.nav.helse.flex.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate

data class AnnulleringDto(
    val orgnummer: String?,
    val organisasjonsnummer: String?,
    val fødselsnummer: String,
    val fom: LocalDate?,
    val tom: LocalDate?
)

fun String.tilAnnulleringDto(): AnnulleringDto = objectMapper.readValue(this)
