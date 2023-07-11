package no.nav.helse.flex.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate

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
    val grunnlagForSykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>?,
    val begrensning: String?, // ER_6G_BEGRENSET, ER_IKKE_6G_BEGRENSET, VURDERT_I_INFOTRYGD og VET_IKKE
    val utbetalingId: String?,
    val vedtakFattetTidspunkt: LocalDate?,
    val sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta? = null,
    val begrunnelser: List<Begrunnelse>? = null
)

sealed class Sykepengegrunnlagsfakta {
    data class IInfotrygd(
        val fastsatt: String,
        val omregnetÅrsinntekt: Double
    ) : Sykepengegrunnlagsfakta()

    data class EtterHovedregel(
        val fastsatt: String,
        val arbeidsgivere: List<Arbeidsgiver>
    ) : Sykepengegrunnlagsfakta()

    data class EtterSkjønn(
        val fastsatt: String,
        val skjønnsfastsatt: Double,
        val arbeidsgivere: List<ArbeidsgiverMedSkjønn>,
        val omregnetÅrsinntekt: Double,
        val innrapportertÅrsinntekt: Double,
        val avviksprosent: Double,
        val `6G`: Double,
        val tags: List<String>
    ) : Sykepengegrunnlagsfakta()
}

data class Arbeidsgiver(
    val arbeidsgiver: String,
    val omregnetÅrsinntekt: Double
)

data class ArbeidsgiverMedSkjønn(
    val arbeidsgiver: String,
    val omregnetÅrsinntekt: Double,
    val skjønnsfastsatt: Double
)

data class Begrunnelse(
    val årsak: String,
    val begrunnelse: String,
    val perioder: List<PeriodeImpl>
)

fun String.tilVedtakFattetForEksternDto(): VedtakFattetForEksternDto = objectMapper.readValue(this)
