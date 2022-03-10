# spinnsyn-backend

Flex har ansvar for vise vedtak gjort som følge av søknad om sykepenger. Vedtakene blir gjort i saksbehandingssystemet Speil, som er eid av Team Bømlo. Team Flex mottar vedtakene som meldigner på Kafka og lagrer de for visning.

## Inkommenda data
Data input til appen er topicene med utbetalinger, vedtak, annulleringer og sykepengesøknader. 
Sykepengesøknadene brukers til å lage et map mellom orgnummer og orgnavn for å slippe å gjøre eksternt oppslag.


## Data
Applikasjonen har en database i GCP.

Vedtakene, utbetalingene og annulleringene er personidentifiserbare, det er ingen sletting av disse dataene.
Det slettes ikke fra tabellen med organisasjonsnummer og organisasjonsnavn.

Applikasjonen eier også et topic som forteller status på vedtak, om de er mottatt, lest etc. 
På topicet finnes også fødselsnummer og dataene er derfor personidentifiserbare.
Topicet har evig retention

# Komme i gang

Bygges med gradle. Standard spring boot oppsett.

---

# Henvendelser


Spørsmål knyttet til koden eller prosjektet kan stilles til flex@nav.no

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #flex.
