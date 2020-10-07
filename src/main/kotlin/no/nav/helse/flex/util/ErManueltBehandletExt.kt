package no.nav.helse.flex.util

import no.nav.helse.flex.vedtak.domene.tilVedtakDto

fun String.erManueltBehandlet(): Boolean = !this.erAutomatiskBehandlet()

fun String.erAutomatiskBehandlet(): Boolean {
    return this.tilVedtakDto().automatiskBehandling
}
