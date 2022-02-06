package no.nav.helse.flex.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate
import java.util.*

data class VedtakDto(
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val automatiskBehandling: Boolean = false,
    val sykepengegrunnlag: Double? = null,
    val grunnlagForSykepengegrunnlag: Double? = null,
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>? = null,
    val begrensning: String? = null, // ER_6G_BEGRENSET, ER_IKKE_6G_BEGRENSET, VURDERT_I_INFOTRYGD og VET_IKKE
    val månedsinntekt: Double? = null,
    val organisasjonsnummer: String? = null,
    val utbetalinger: List<UtbetalingDto> = emptyList(),
    val dokumenter: List<Dokument> = emptyList()
) {
    data class UtbetalingDto(
        val mottaker: String,
        val fagområde: String,
        val totalbeløp: Int,
        val utbetalingslinjer: List<UtbetalingslinjeDto> = emptyList()
    ) {
        data class UtbetalingslinjeDto(
            val fom: LocalDate,
            val tom: LocalDate,
            val dagsats: Int,
            val beløp: Int,
            val grad: Double,
            val sykedager: Int
        )
    }
}

data class Dokument(val dokumentId: UUID, val type: Type) {
    enum class Type {
        Sykmelding, Søknad, Inntektsmelding
    }
}

fun String.tilVedtakDto(): VedtakDto = objectMapper.readValue(this)
