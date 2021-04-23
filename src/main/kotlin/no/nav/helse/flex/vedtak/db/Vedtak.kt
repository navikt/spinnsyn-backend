package no.nav.helse.flex.vedtak.db

import no.nav.helse.flex.vedtak.domene.VedtakDto
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
