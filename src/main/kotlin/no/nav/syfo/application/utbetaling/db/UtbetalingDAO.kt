package no.nav.syfo.application.utbetaling.db

import no.nav.syfo.application.utbetaling.model.Utbetaling
import no.nav.syfo.db.DatabaseInterface

fun DatabaseInterface.registrerUtbetaling(utbetaling: Utbetaling) {
    connection.use {
        val insertQuery = """
            INSERT INTO utbetaling (
                id, 
                aktoer_id,
                pasientfnr) 
            VALUES (?, ?)
        """.trimIndent()

        var i = 1
        connection.prepareStatement(insertQuery).use {
            it.setObject(i++, utbetaling.id)
            it.setString(i++, utbetaling.aktørId)
            it.setString(i++, utbetaling.fødselsnummer)
            it.execute()
        }

        connection.commit()
    }
}
