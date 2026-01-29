package no.nav.helse.flex.jobb

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.VedtakDbRecord
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class MigrerTilUtbetalingsdagerBatchMigratorTest : FellesTestOppsett() {
    @Autowired
    private lateinit var batchMigrator: MigrerTilUtbetalingsdagerBatchMigrator

    @BeforeEach
    fun setup() {
        vedtakRepository.deleteAll()
        utbetalingRepository.deleteAll()
    }

    @Test
    fun `migrerer utbetaling med gammelt format til nytt format`() {
        val utbetalingId = "utbetaling-id"

        val vedtak =
            vedtakRepository.save(
                VedtakDbRecord(
                    utbetalingId = utbetalingId,
                    fnr = "12345678910",
                    vedtak = VEDTAK_JSON,
                    opprettet = Instant.parse("2021-01-01T12:00:00Z"),
                ),
            )

        val utbetaling =
            utbetalingRepository.save(
                UtbetalingDbRecord(
                    fnr = "12345678910",
                    utbetalingType = "UTBETALING",
                    utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                    opprettet = Instant.parse("2021-01-01T12:00:00Z"),
                    utbetalingId = utbetalingId,
                    antallVedtak = 1,
                ),
            )

        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(utbetaling to listOf(vedtak)))

        resultat.migrert.`should be equal to`(1)
        resultat.feilet.`should be equal to`(0)
    }

    @Test
    fun `feiler når utbetaling ikke har tilhørende vedtak`() {
        val utbetaling =
            UtbetalingDbRecord(
                id = "id-1",
                fnr = "12345678910",
                utbetalingType = "UTBETALING",
                utbetaling = UTBETALING_GAMMELT_FORMAT_JSON,
                opprettet = Instant.now(),
                utbetalingId = "mangler-vedtak",
                antallVedtak = 1,
            )

        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(utbetaling to emptyList()))

        resultat.migrert.`should be equal to`(0)
        resultat.feilet.`should be equal to`(1)
    }

    @Test
    fun `feiler når utbetaling har ugyldig json`() {
        val utbetalingId = "ugyldig-json-id"
        val vedtak =
            VedtakDbRecord(
                utbetalingId = utbetalingId,
                fnr = "12345678910",
                vedtak = VEDTAK_JSON,
                opprettet = Instant.parse("2021-01-01T12:00:00Z"),
            )
        val utbetaling =
            UtbetalingDbRecord(
                id = "id-2",
                fnr = "12345678910",
                utbetalingType = "UTBETALING",
                utbetaling = "{ikke gyldig json",
                opprettet = Instant.now(),
                utbetalingId = utbetalingId,
                antallVedtak = 1,
            )
        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(utbetaling to listOf(vedtak)))

        resultat.migrert.`should be equal to`(0)
        resultat.feilet.`should be equal to`(1)
    }

    @Test
    fun `feiler når utbetaling mangler utbetalingsdager`() {
        val utbetalingId = "uten-dager"
        val vedtak =
            VedtakDbRecord(
                utbetalingId = utbetalingId,
                fnr = "12345678910",
                vedtak = VEDTAK_JSON,
                opprettet = Instant.parse("2021-01-01T12:00:00Z"),
            )
        val utbetaling =
            UtbetalingDbRecord(
                fnr = "12345678910",
                utbetalingType = "UTBETALING",
                utbetaling = UTBETALING_UTEN_UTBETALINGSDAGER_JSON,
                opprettet = Instant.now(),
                utbetalingId = utbetalingId,
                antallVedtak = 1,
            )

        val lagretUtbetaling = utbetalingRepository.save(utbetaling)

        val resultat = batchMigrator.migrerGammeltVedtak(mapOf(lagretUtbetaling to listOf(vedtak)))

        resultat.migrert.`should be equal to`(0)
        resultat.feilet.`should be equal to`(1)

        utbetalingRepository
            .findById(lagretUtbetaling.id!!)
            .also { utbetalingEtterpå ->
                utbetalingEtterpå.isPresent.`should be equal to`(true)
                utbetalingEtterpå
                    .get()
                    .utbetaling
                    .contains("\"utbetalingsdager\"")
                    .`should be equal to`(false)
            }
    }
}

const val UTBETALING_UTEN_UTBETALINGSDAGER_JSON = """
    {"utbetalingId":"81e48af0-1936-4b69-b5da-38c61d3ba6bf","korrelasjonsId":"9c4c4c14-85b8-4b38-8518-9d299ff81936","fødselsnummer":"57907801103","aktørId":"2223759944574","organisasjonsnummer":"947064649","fom":"2024-05-01","tom":"2024-05-31","forbrukteSykedager":26,"gjenståendeSykedager":222,"stønadsdager":22,"automatiskBehandling":false,"arbeidsgiverOppdrag":{"mottaker":"947064649","fagområde":"SPREF","fagsystemId":"MJBEMEHTU5BKZO3Q4SBPU2UZ3M","nettoBeløp":45694,"stønadsdager":22,"fom":"2024-05-01","tom":"2024-05-31","utbetalingslinjer":[{"fom":"2024-05-01","tom":"2024-05-14","dagsats":2077,"totalbeløp":20770,"grad":100.0,"stønadsdager":10},{"fom":"2024-05-16","tom":"2024-05-31","dagsats":2077,"totalbeløp":24924,"grad":100.0,"stønadsdager":12}]},"personOppdrag":null,"type":"UTBETALING","foreløpigBeregnetSluttPåSykepenger":"2025-04-08","event":"utbetaling_utbetalt","versjon":"1.0.0","antallVedtak":1}
"""

const val UTBETALING_GAMMELT_FORMAT_JSON = """
    {"utbetalingId":"3d19a9d1-c285-4dcd-abb3-b3bcfb538c4a","korrelasjonsId":"139f6ed1-fb55-4272-a027-b9b763392a4b","fødselsnummer":"57907801103","aktørId":"2223759944574","organisasjonsnummer":"947064649","fom":"2024-05-01","tom":"2024-05-31","forbrukteSykedager":26,"gjenståendeSykedager":222,"stønadsdager":22,"automatiskBehandling":false,"arbeidsgiverOppdrag":{"mottaker":"947064649","fagområde":"SPREF","fagsystemId":"MJBEMEHTU5BKZO3Q4SBPU2UZ3M","nettoBeløp":45694,"stønadsdager":22,"fom":"2024-05-01","tom":"2024-05-31","utbetalingslinjer":[{"fom":"2024-05-01","tom":"2024-05-14","dagsats":2077,"totalbeløp":20770,"grad":100.0,"stønadsdager":10},{"fom":"2024-05-16","tom":"2024-05-31","dagsats":2077,"totalbeløp":24924,"grad":100.0,"stønadsdager":12}]},"personOppdrag":null,"type":"UTBETALING","utbetalingsdager":[{"dato":"2024-05-01","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-02","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-03","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-04","type":"NavHelgDag","begrunnelser":[]},{"dato":"2024-05-05","type":"NavHelgDag","begrunnelser":[]},{"dato":"2024-05-06","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-07","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-08","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-09","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-10","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-11","type":"NavHelgDag","begrunnelser":[]},{"dato":"2024-05-12","type":"NavHelgDag","begrunnelser":[]},{"dato":"2024-05-13","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-14","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-15","type":"AvvistDag","grad":0,"begrunnelser":["MinimumInntektOver67"]},{"dato":"2024-05-16","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-17","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-18","type":"NavHelgDag","begrunnelser":[]},{"dato":"2024-05-19","type":"NavHelgDag","begrunnelser":[]},{"dato":"2024-05-20","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-21","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-22","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-23","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-24","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-25","type":"NavHelgDag","begrunnelser":[]},{"dato":"2024-05-26","type":"NavHelgDag","begrunnelser":[]},{"dato":"2024-05-27","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-28","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-29","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-30","type":"NavDag","begrunnelser":[]},{"dato":"2024-05-31","type":"NavDag","begrunnelser":[]}],"foreløpigBeregnetSluttPåSykepenger":"2025-04-08","event":"utbetaling_utbetalt","versjon":"1.0.0","antallVedtak":1}
"""

const val VEDTAK_JSON = """
    {"fødselsnummer":"57907801103","aktørId":"2223759944574","organisasjonsnummer":"947064649","yrkesaktivitetstype":"ARBEIDSTAKER","fom":"2024-05-01","tom":"2024-05-31","skjæringstidspunkt":"2024-05-01","dokumenter":[{"dokumentId":"a7bdd7b8-8ea4-4af5-bdc3-9a8dbdb3e816","type":"Søknad"},{"dokumentId":"9ee8d87c-57d7-4e97-b94f-c73d161134b9","type":"Sykmelding"}],"sykepengegrunnlag":540000.0,"utbetalingId":"3d19a9d1-c285-4dcd-abb3-b3bcfb538c4a","vedtakFattetTidspunkt":"2026-01-14T11:52:04.280812329","sykepengegrunnlagsfakta":{"fastsatt":"EtterHovedregel","omregnetÅrsinntekt":540000.0,"innrapportertÅrsinntekt":540000.0,"avviksprosent":0.0,"6G":744168.0,"tags":[],"arbeidsgivere":[{"arbeidsgiver":"947064649","omregnetÅrsinntekt":540000.0}]},"begrunnelser":[{"type":"Innvilgelse","begrunnelse":"","perioder":[{"fom":"2024-05-01","tom":"2024-05-31"}]}],"tags":["IngenNyArbeidsgiverperiode"],"saksbehandler":{"navn":"Marte Sømo Solberg","ident":"S165568"},"beslutter":null,"versjon":"1.2.2","begrensning":"ER_IKKE_6G_BEGRENSET","inntekt":45000.0,"grunnlagForSykepengegrunnlag":540000.0,"grunnlagForSykepengegrunnlagPerArbeidsgiver":{"947064649":540000.0}}
"""
