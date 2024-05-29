package no.nav.helse.flex.domene

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

data class RSVedtakWrapper(
    val id: String,
    val lest: Boolean,
    val lestDato: OffsetDateTime? = null,
    val vedtak: RSVedtak,
    val opprettetTimestamp: Instant,
    val orgnavn: String,
    val annullert: Boolean = false,
    val revurdert: Boolean = false,
    @JsonIgnore
    val dagerArbeidsgiver: List<RSDag> = emptyList(),
    @JsonIgnore
    val dagerPerson: List<RSDag> = emptyList(),
    @JsonIgnore
    val sykepengebelopArbeidsgiver: Int = 0,
    @JsonIgnore
    val sykepengebelopPerson: Int = 0,
    val andreArbeidsgivere: Map<String, Double>?,
    val organisasjoner: Map<String, String> = emptyMap(),
)

data class RSVedtak(
    val organisasjonsnummer: String,
    override val fom: LocalDate,
    override val tom: LocalDate,
    val dokumenter: List<Dokument>,
    val inntekt: Double?,
    val sykepengegrunnlag: Double?,
    val utbetaling: RSUtbetalingUtbetalt,
    val grunnlagForSykepengegrunnlag: Double?,
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>?,
    // ER_6G_BEGRENSET, ER_IKKE_6G_BEGRENSET, VURDERT_I_INFOTRYGD og VET_IKKE
    val begrensning: String?,
    val vedtakFattetTidspunkt: LocalDate?,
    val sykepengegrunnlagsfakta: JsonNode?,
    val begrunnelser: List<Begrunnelse>?,
    val tags: List<String>?,
) : Periode

data class RSUtbetalingUtbetalt(
    val organisasjonsnummer: String?,
    val utbetalingId: String?,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: RSOppdrag?,
    val personOppdrag: RSOppdrag?,
    val utbetalingsdager: List<RSUtbetalingdag>?,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate?,
    val utbetalingType: String,
)

data class RSOppdrag(
    val utbetalingslinjer: List<RSUtbetalingslinje>,
)

data class RSUtbetalingslinje(
    val fom: LocalDate,
    val tom: LocalDate,
    val dagsats: Int,
    val totalbeløp: Int,
    val grad: Double,
    val stønadsdager: Int,
) {
    fun overlapperMed(dato: LocalDate) = dato in fom..tom
}

data class RSUtbetalingdag(
    val dato: LocalDate,
    val type: String,
    val begrunnelser: List<String>,
)

data class RSDag(
    val dato: LocalDate,
    val belop: Int,
    val grad: Double,
    val dagtype: String,
    val begrunnelser: List<String>,
)
