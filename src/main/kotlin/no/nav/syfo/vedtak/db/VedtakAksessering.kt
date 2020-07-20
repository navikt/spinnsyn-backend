package no.nav.syfo.vedtak.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import java.sql.Connection
import java.sql.ResultSet

fun Connection.finnVedtak(fnr: String): List<Vedtak> =
    this.prepareStatement(
        """
            SELECT id, vedtak
            FROM  vedtak
            WHERE fnr = ?;
            """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toSykmelding() }
    }

fun ResultSet.toSykmelding(): Vedtak =
    Vedtak(id = getString("id"), vedtak = objectMapper.readValue(getString("vedtak")))


data class Vedtak(val id: String, val vedtak: Any)
