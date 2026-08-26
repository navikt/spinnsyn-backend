package no.nav.helse.flex.domene

import no.nav.helse.flex.objectMapper
import tools.jackson.module.kotlin.readValue

data class VedtakStatusDTO(
    val id: String,
    val fnr: String,
    val vedtakStatus: VedtakStatus,
)

enum class VedtakStatus {
    MOTATT,
    LEST,
}

fun String.tilVedtakStatusDto(): VedtakStatusDTO = objectMapper.readValue(this)
