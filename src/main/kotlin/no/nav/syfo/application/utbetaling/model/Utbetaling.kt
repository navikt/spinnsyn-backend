package no.nav.syfo.application.utbetaling.model

import java.util.UUID

data class Utbetaling(
    val id: UUID,
    val aktørId: String,
    val fødselsnummer: String
)
