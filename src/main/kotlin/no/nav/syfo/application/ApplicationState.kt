package no.nav.syfo.application

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = false
)
