package no.nav.helse.flex.testdata

import no.nav.helse.flex.domene.Dekning
import no.nav.helse.flex.domene.Forsikringsvurdering

fun lagForsikringsvurdering() =
    Forsikringsvurdering(
        forsikringsvurderingId = "forsikringsvurdering-123",
        individuellForsikringNavn = "Individuell Forsikring AS",
        kollektivForsikringNavn = "Kollektiv Forsikring AS",
        dekning = Dekning(grad = 80, fraDag = 1),
    )
