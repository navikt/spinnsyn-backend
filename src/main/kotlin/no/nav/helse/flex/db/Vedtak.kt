package no.nav.helse.flex.db

import no.nav.helse.flex.domene.VedtakDto
import org.springframework.data.annotation.Id
import java.time.Instant
import java.time.OffsetDateTime

data class Vedtak(
    @Id
    val id: String,
    val lest: Boolean,
    val lestDato: OffsetDateTime?,
    val vedtak: VedtakDto,
    val opprettet: Instant
)
