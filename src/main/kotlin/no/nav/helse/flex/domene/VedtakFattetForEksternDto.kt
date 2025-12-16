package no.nav.helse.flex.domene

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate

data class VedtakFattetForEksternDto(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val yrkesaktivitetstype: String?,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val dokumenter: List<Dokument>,
    val inntekt: Double,
    val sykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>?,
    // ER_6G_BEGRENSET, ER_IKKE_6G_BEGRENSET, VURDERT_I_INFOTRYGD og VET_IKKE
    val begrensning: String?,
    val utbetalingId: String?,
    val vedtakFattetTidspunkt: LocalDate?,
    val sykepengegrunnlagsfakta: JsonNode? = null,
    val begrunnelser: List<Begrunnelse>? = null,
    val tags: List<String>? = null,
    val saksbehandler: Saksbehandler? = null,
    val beslutter: Saksbehandler? = null,
)

data class Begrunnelse(
    val type: String,
    val begrunnelse: String,
    val perioder: List<PeriodeImpl>,
)

data class Saksbehandler(
    val navn: String,
    val ident: String,
)

fun String.tilVedtakFattetForEksternDto(): VedtakFattetForEksternDto = objectMapper.readValue(this)
