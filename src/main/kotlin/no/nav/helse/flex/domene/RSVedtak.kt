package no.nav.helse.flex.domene

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.flex.logger
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
    val daglisteArbeidsgiver: List<RSDag> = emptyList(),
    val daglisteSykmeldt: List<RSDag> = emptyList(),
    val andreArbeidsgivere: Map<String, Double>?,
    val organisasjoner: Map<String, String> = emptyMap(),
) {
    companion object {
        val log = logger()

        fun dagerTilUtbetalingsdager(
            dagerPerson: List<RSDag>,
            dagerArbeidsgiver: List<RSDag>,
        ): List<RSUtbetalingdag> {
            val fom = (dagerPerson + dagerArbeidsgiver).minByOrNull { it.dato }?.dato ?: return emptyList()
            val tom = (dagerPerson + dagerArbeidsgiver).maxByOrNull { it.dato }?.dato ?: return emptyList()
            val utbetalingsdager = mutableListOf<RSUtbetalingdag>()

            for (dato in fom.datesUntil(tom.plusDays(1))) {
                val dagPerson = dagerPerson.find { it.dato.equals(dato) }
                val dagArbeidsgiver = dagerArbeidsgiver.find { it.dato.equals(dato) }

                if (dagPerson == null && dagArbeidsgiver == null) {
                    log.warn("Fant ikke dag for dato $dato")
                    continue
                }

                val dagtype: String
                val grad: Double
                val begrunnelser: List<String>

                when {
                    dagPerson != null && dagArbeidsgiver != null -> {
                        if (dagPerson.dagtype != dagArbeidsgiver.dagtype) {
                            log.warn("Ulike dagtyper for dato $dato: Person=${dagPerson.dagtype} Arbeidsgiver=${dagArbeidsgiver.dagtype}")
                        }
                        if (dagPerson.grad != dagArbeidsgiver.grad) {
                            log.warn("Ulike grader for dato $dato: Person=${dagPerson.grad} Arbeidsgiver=${dagArbeidsgiver.grad}")
                        }
                        dagtype = dagPerson.dagtype
                        grad = dagPerson.grad
                        begrunnelser = dagPerson.begrunnelser + dagArbeidsgiver.begrunnelser
                    }

                    dagPerson != null -> {
                        dagtype = dagPerson.dagtype
                        grad = dagPerson.grad
                        begrunnelser = dagPerson.begrunnelser
                    }

                    else -> {
                        dagtype = dagArbeidsgiver!!.dagtype
                        grad = dagArbeidsgiver.grad
                        begrunnelser = dagArbeidsgiver.begrunnelser
                    }
                }

                utbetalingsdager.add(
                    RSUtbetalingdag(
                        dato = dato,
                        type = dagtype,
                        begrunnelser = begrunnelser,
                        beløpTilSykmeldt = dagPerson?.belop ?: 0,
                        beløpTilArbeidsgiver = dagArbeidsgiver?.belop ?: 0,
                        sykdomsgrad = grad.toInt(),
                    ),
                )
            }

            return utbetalingsdager.sortedBy { it.dato }
        }
    }
}

data class RSVedtak(
    val organisasjonsnummer: String,
    val yrkesaktivitetstype: String,
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
    val saksbehandler: Saksbehandler? = null,
    val beslutter: Saksbehandler? = null,
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
    val beløpTilArbeidsgiver: Int? = null,
    val beløpTilSykmeldt: Int? = null,
    val sykdomsgrad: Int? = null,
) {
    companion object {
        fun konverterTilUtbetalindagDto(utbetalt: RSUtbetalingdag) =
            UtbetalingUtbetalt.UtbetalingdagDto(
                dato = utbetalt.dato,
                type = utbetalt.type,
                begrunnelser =
                    utbetalt.begrunnelser.map {
                        UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.valueOf(it)
                    },
                beløpTilArbeidsgiver = utbetalt.beløpTilArbeidsgiver,
                beløpTilSykmeldt = utbetalt.beløpTilSykmeldt,
                sykdomsgrad = utbetalt.sykdomsgrad,
            )
    }
}

data class RSDag(
    val dato: LocalDate,
    val belop: Int,
    val grad: Double,
    val dagtype: String,
    val begrunnelser: List<String>,
)
