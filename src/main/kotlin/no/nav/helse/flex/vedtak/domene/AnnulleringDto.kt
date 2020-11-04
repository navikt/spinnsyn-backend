package no.nav.helse.flex.vedtak.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate
import java.time.LocalDateTime

data class AnnulleringDto(
    val orgnummer: String,
    val tidsstempel: LocalDateTime,
    val fødselsnummer: String,
    val fom: LocalDate?,
    val tom: LocalDate?
)

fun String.tilAnnulleringDto(): AnnulleringDto = objectMapper.readValue(this)

fun AnnulleringDto.serialisertTilString(): String = objectMapper.writeValueAsString(this)
