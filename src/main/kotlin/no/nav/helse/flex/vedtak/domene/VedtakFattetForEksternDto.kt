package no.nav.helse.flex.vedtak.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate
import java.util.UUID

data class VedtakFattetForEksternDto(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val dokumenter: List<Dokument>,
    val inntekt: Double,
    val sykepengegrunnlag: Double,
    val utbetalingId: UUID?
)

fun String.tilVedtakFattetForEksternDto(): VedtakFattetForEksternDto = objectMapper.readValue(this)
