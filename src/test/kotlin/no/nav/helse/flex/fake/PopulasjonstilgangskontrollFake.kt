package no.nav.helse.flex.fake

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat

class PopulasjonstilgangskontrollFake : PopulasjonstilgangskontrollProvider {
    var resultat: () -> TilgangskontrollResultat = { TilgangskontrollResultat.Ok }
    val kjerneoppslag = mutableListOf<String>()

    fun nullstill() {
        resultat = { TilgangskontrollResultat.Ok }
        kjerneoppslag.clear()
    }

    override fun kontrollerKjerneTilgang(
        accessToken: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat {
        kjerneoppslag.add(fødselsnummer)
        return resultat()
    }

    override fun kontrollerKomplettTilgang(
        accessToken: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat = throw UnsupportedOperationException("Brukes ikke i spinnsyn")

    override fun kontrollerKjerneTilgangForAnsatt(
        ansattId: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat = throw UnsupportedOperationException("Brukes ikke i spinnsyn")
}
