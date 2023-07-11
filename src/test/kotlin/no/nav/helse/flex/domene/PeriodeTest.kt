package no.nav.helse.flex.domene

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodeTest {

    @Test
    fun `0101 0401 overlapper 0301 0501 og vice versa`() {
        val første = ("01.01" to "04.01").periode()
        val andre = ("03.01" to "05.01").periode()
        første.overlapper(andre) shouldBeEqualTo true
        andre.overlapper(første) shouldBeEqualTo true
    }

    @Test
    fun `0101 0101 og 0101 0101 overlapper`() {
        val første = ("01.01" to "01.01").periode()
        første.overlapper(første) shouldBeEqualTo true
    }

    @Test
    fun `0101 0101 og 0201 0201 overlapper ikke`() {
        val første = ("01.01" to "01.01").periode()
        val andre = ("02.01" to "02.01").periode()
        første.overlapper(andre) shouldBeEqualTo false
        andre.overlapper(første) shouldBeEqualTo false
    }

    @Test
    fun `0101 0201 og 0201 0301 overlapper`() {
        val første = ("01.01" to "02.01").periode()
        val andre = ("02.01" to "03.01").periode()
        første.overlapper(andre) shouldBeEqualTo true
        andre.overlapper(første) shouldBeEqualTo true
    }

    @Test
    fun `0101 dfg1 overlapper`() {
        val vedtak = """{"fødselsnummer":"27427123583","aktørId":"2974611804459","organisasjonsnummer":"896929119","fom":"2023-04-01","tom":"2023-04-30","skjæringstidspunkt":"2023-04-01","dokumenter":[{"dokumentId":"29624083-9b0a-4f52-9a2c-58bb4e590b7e","type":"Søknad"},{"dokumentId":"29a226ef-2431-4a53-a1e0-ab59b4ad40fd","type":"Inntektsmelding"},{"dokumentId":"fd996cf0-8c56-4b12-bc2e-e94edd3bd5c6","type":"Sykmelding"}],"inntekt":40000.0,"sykepengegrunnlag":480000.0,"grunnlagForSykepengegrunnlag":480000.0,"grunnlagForSykepengegrunnlagPerArbeidsgiver":{"896929119":480000.0},"begrensning":"ER_IKKE_6G_BEGRENSET","utbetalingId":"e3a46488-c75d-4ef2-a98c-117d94ce2e43","vedtakFattetTidspunkt":"2023-07-06T12:29:41.114900073","versjon":"1.1.0","begrunnelser":[],"sykepengegrunnlagsfakta":{"fastsatt":"EtterHovedregel","omregnetÅrsinntekt":480000.0,"innrapportertÅrsinntekt":480000.0,"avviksprosent":0.0,"6G":668862.0,"tags":[],"arbeidsgivere":[{"arbeidsgiver":"896929119","omregnetÅrsinntekt":480000.0}]}}"""
        vedtak.tilVedtakFattetForEksternDto().fødselsnummer.shouldBeEqualTo("27427123583")
    }
}

fun Pair<String, String>.periode(): Periode {
    val fom = this.first.split(".").map { Integer.parseInt(it) }
    val tom = this.second.split(".").map { Integer.parseInt(it) }
    return PeriodeImpl(LocalDate.of(2020, fom.last(), fom.first()), LocalDate.of(2020, tom.last(), tom.first()))
}
