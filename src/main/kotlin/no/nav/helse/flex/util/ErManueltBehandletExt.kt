package no.nav.helse.flex.util

import com.fasterxml.jackson.databind.ObjectMapper

fun String.erManueltBehandlet(): Boolean = !this.erAutomatiskBehandlet()

fun String.erAutomatiskBehandlet(): Boolean {
    val get = ObjectMapper()
        .readTree(this)
        .get("automatiskBehandling") ?: return false
    return get.asBoolean(false)
}
