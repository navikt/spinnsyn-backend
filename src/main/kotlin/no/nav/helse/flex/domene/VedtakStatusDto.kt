package no.nav.helse.flex.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper

data class VedtakStatusDTO(
    val id: String,
    val fnr: String,
    val vedtakStatus: VedtakStatus
)

enum class VedtakStatus {
    MOTATT,
    LEST,
}

fun String.tilVedtakStatusDto(): VedtakStatusDTO = objectMapper.readValue(this)
