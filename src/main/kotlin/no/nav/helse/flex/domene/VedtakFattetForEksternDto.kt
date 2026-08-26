package no.nav.helse.flex.domene

import no.nav.helse.flex.objectMapper
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.readValue
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
    // Defaultene på de primitive non-null feltene gjør at oppførsel fra Jackson 2 videreføres siden
    // Jackson 3 feiler på manglende felter.
    val inntekt: Double = 0.0,
    val sykepengegrunnlag: Double = 0.0,
    val grunnlagForSykepengegrunnlag: Double = 0.0,
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
    val forsikringsvurdering: Forsikringsvurdering? = null,
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

data class Forsikringsvurdering(
    val forsikringsvurderingId: String,
    val individuellForsikringNavn: String?,
    val kollektivForsikringNavn: String?,
    val dekning: Dekning?,
)

data class Dekning(
    val grad: Int,
    val fraDag: Int,
)

fun String.tilVedtakFattetForEksternDto(): VedtakFattetForEksternDto = objectMapper.readValue(this)
