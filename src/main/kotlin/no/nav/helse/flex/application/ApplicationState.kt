package no.nav.helse.flex.application

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = false
)
