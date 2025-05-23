package no.nav.helse.flex.service

import no.nav.helse.flex.client.pdl.AKTORID
import no.nav.helse.flex.client.pdl.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.client.pdl.ResponseData
import org.springframework.stereotype.Component
import java.io.Serializable

@Component
class IdentService(
    private val pdlClient: PdlClient,
) {
    private fun List<PdlIdent>.folkeregisteridenter(): List<String> = this.filter { it.gruppe == FOLKEREGISTERIDENT }.map { it.ident }

    fun hentFolkeregisterIdenterMedHistorikkForFnr(fnr: String): FolkeregisterIdenter {
        val identer = pdlClient.hentIdenterMedHistorikk(fnr)
        return FolkeregisterIdenter(
            originalIdent = fnr,
            andreIdenter = identer.folkeregisteridenter().filterNot { it == fnr },
        )
    }

    private fun ResponseData.aktorId(): String =
        this.hentIdenter
            ?.identer
            ?.find { it.gruppe == AKTORID }
            ?.ident
            ?: throw RuntimeException("Kunne ikke finne aktørid i pdl response")
}

data class FolkeregisterIdenter(
    val originalIdent: String,
    val andreIdenter: List<String>,
) : Serializable {
    fun alle(): List<String> = mutableListOf(originalIdent).also { it.addAll(andreIdenter) }
}
