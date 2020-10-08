package no.nav.helse.flex.testutil

import no.nav.helse.flex.vedtak.domene.VedtakDto

fun VedtakDto.somKunRefusjon(fnr: String = "01010112345", orgnummer: String = "999999999"): VedtakDto {
    return this.copy(
        utbetalinger = listOf(
            VedtakDto.UtbetalingDto(
                mottaker = orgnummer,
                totalbeløp = 3499,
                fagområde = "SPREF"
            ),
            VedtakDto.UtbetalingDto(
                mottaker = fnr,
                totalbeløp = 0,
                fagområde = "SP"
            )
        )
    )
}

fun VedtakDto.somUtbetalingTilBruker(fnr: String = "01010112345", orgnummer: String = "999999999"): VedtakDto {
    return this.copy(
        utbetalinger = listOf(
            VedtakDto.UtbetalingDto(
                mottaker = orgnummer,
                totalbeløp = 0,
                fagområde = "SPREF"
            ),
            VedtakDto.UtbetalingDto(
                mottaker = fnr,
                totalbeløp = 4568,
                fagområde = "SP"
            )
        )
    )
}
