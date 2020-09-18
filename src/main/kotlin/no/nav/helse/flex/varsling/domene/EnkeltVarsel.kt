package no.nav.helse.flex.varsling.domene

data class EnkeltVarsel(
    val fodselsnummer: String,
    val varselTypeId: String,
    val varselBestillingId: String
)
