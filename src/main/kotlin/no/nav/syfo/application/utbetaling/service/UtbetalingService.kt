package no.nav.syfo.application.utbetaling.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.db.DatabaseInterface

class UtbetalingService @KtorExperimentalAPI constructor(
    private val database: DatabaseInterface
)
