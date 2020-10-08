package no.nav.helse.flex.vedtak

import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.testutil.somKunRefusjon
import no.nav.helse.flex.vedtak.db.Vedtak
import no.nav.helse.flex.vedtak.domene.Dokument
import no.nav.helse.flex.vedtak.domene.VedtakDto
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should not contain`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@KtorExperimentalAPI
object HentInntektsmeldingFraFørsteVedtakSpek : Spek({

    describe("Test hent inntektsmeding fra første vedtak") {
        it("Et vedtak kant i kant med vedtak som hadde inntektsmelding") {

            val inntektsmelding = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Inntektsmelding)
            val søknad = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Søknad)

            val forsteVedtak = VedtakDto(
                fom = LocalDate.now().minusDays(20),
                tom = LocalDate.now(),
                automatiskBehandling = false,
                gjenståendeSykedager = 200,
                forbrukteSykedager = 23,
                dokumenter = listOf(inntektsmelding, søknad)
            ).somKunRefusjon().somVedtak()

            val nesteVedtak = forsteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = forsteVedtak.vedtak.tom.plusDays(1),
                tom = forsteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somVedtak()

            val resultat = listOf(forsteVedtak, nesteVedtak).hentInntektsmeldingFraFørsteVedtak()

            resultat.size `should be` 2

            resultat.find { it.id == forsteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == forsteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
        }

        it("To vedtak kant i kant med vedtak som hadde inntektsmelding") {

            val inntektsmelding = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Inntektsmelding)
            val søknad = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Søknad)

            val forsteVedtak = VedtakDto(
                fom = LocalDate.now().minusDays(20),
                tom = LocalDate.now(),
                automatiskBehandling = false,
                gjenståendeSykedager = 200,
                forbrukteSykedager = 23,
                dokumenter = listOf(inntektsmelding, søknad)
            ).somKunRefusjon().somVedtak()

            val nesteVedtak = forsteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = forsteVedtak.vedtak.tom.plusDays(1),
                tom = forsteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somVedtak()

            val sisteVedtak = nesteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = nesteVedtak.vedtak.tom.plusDays(1),
                tom = nesteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somVedtak()

            val resultat = listOf(forsteVedtak, nesteVedtak, sisteVedtak).hentInntektsmeldingFraFørsteVedtak()

            resultat.size `should be` 3

            resultat.find { it.id == forsteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == forsteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
            resultat.find { it.id == sisteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == sisteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
        }

        it("To vedtak kant i kant med vedtak som hadde inntektsmelding hvor det ene også er korrigert / dobbelt") {

            val inntektsmelding = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Inntektsmelding)
            val søknad = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Søknad)

            val forsteVedtak = VedtakDto(
                fom = LocalDate.now().minusDays(20),
                tom = LocalDate.now(),
                automatiskBehandling = false,
                gjenståendeSykedager = 200,
                forbrukteSykedager = 23,
                dokumenter = listOf(inntektsmelding, søknad)
            ).somKunRefusjon().somVedtak()

            val nesteVedtak = forsteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = forsteVedtak.vedtak.tom.plusDays(1),
                tom = forsteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somVedtak()

            val korrigertVedtak = forsteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = forsteVedtak.vedtak.tom.plusDays(1),
                tom = forsteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somVedtak()

            val sisteVedtak = nesteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = nesteVedtak.vedtak.tom.plusDays(1),
                tom = nesteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somVedtak()

            val resultat = listOf(forsteVedtak, nesteVedtak, korrigertVedtak, sisteVedtak).hentInntektsmeldingFraFørsteVedtak()

            resultat.size `should be` 4

            resultat.find { it.id == forsteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == forsteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
            resultat.find { it.id == korrigertVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == korrigertVedtak.id }!!.vedtak.dokumenter `should contain` søknad
            resultat.find { it.id == sisteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == sisteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
        }

        it("Et vedtak kant i kant med vedtak som hadde inntektsmelding, siste vedtak en dag i mellom") {

            val inntektsmelding = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Inntektsmelding)
            val søknad = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Søknad)

            val forsteVedtak = VedtakDto(
                fom = LocalDate.now().minusDays(20),
                tom = LocalDate.now(),
                automatiskBehandling = false,
                gjenståendeSykedager = 200,
                forbrukteSykedager = 23,
                dokumenter = listOf(inntektsmelding, søknad)
            ).somKunRefusjon().somVedtak()

            val nesteVedtak = forsteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = forsteVedtak.vedtak.tom.plusDays(1),
                tom = forsteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somVedtak()

            val sisteVedtak = nesteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = nesteVedtak.vedtak.tom.plusDays(2),
                tom = nesteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somVedtak()

            val resultat = listOf(forsteVedtak, nesteVedtak, sisteVedtak).hentInntektsmeldingFraFørsteVedtak()

            resultat.size `should be` 3

            resultat.find { it.id == forsteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == forsteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` inntektsmelding
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
            resultat.find { it.id == sisteVedtak.id }!!.vedtak.dokumenter `should not contain` inntektsmelding
            resultat.find { it.id == sisteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
        }

        it("Et vedtak kant i kant med vedtak som hadde inntektsmelding men med forskjellig orgnummer får ikke inntektsmeldingen") {

            val inntektsmelding = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Inntektsmelding)
            val forsteVedtak = VedtakDto(
                fom = LocalDate.now().minusDays(20),
                tom = LocalDate.now(),
                automatiskBehandling = false,
                gjenståendeSykedager = 200,
                forbrukteSykedager = 23,
                dokumenter = listOf(inntektsmelding)
            ).somKunRefusjon(orgnummer = "1").somVedtak()

            val søknad = Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Søknad)
            val nesteVedtak = forsteVedtak.vedtak.copy(
                automatiskBehandling = true,
                fom = forsteVedtak.vedtak.tom.plusDays(1),
                tom = forsteVedtak.vedtak.tom.plusDays(20),
                dokumenter = listOf(søknad)
            ).somKunRefusjon(orgnummer = "2").somVedtak()

            val resultat = listOf(forsteVedtak, nesteVedtak).hentInntektsmeldingFraFørsteVedtak()

            resultat.size `should be` 2

            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should not contain` inntektsmelding
            resultat.find { it.id == nesteVedtak.id }!!.vedtak.dokumenter `should contain` søknad
        }
    }
})

private fun VedtakDto.somVedtak(): Vedtak {
    return Vedtak(id = UUID.randomUUID().toString(), lest = false, vedtak = this, opprettet = Instant.now())
}
