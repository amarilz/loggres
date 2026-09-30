package com.amarildo.loggres.parser;

import com.amarildo.loggres.sql.SqlFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlFormatterTest {

    @Test
    void formatsNestedPostgresJoinQuery() {
        String sql = "select * from (((bot_ca_table LEFT OUTER JOIN bot_valid_table ON bot_ca_table.bot_ca_subj_id=bot_valid_table.bot_cert_subj_id) LEFT OUTER JOIN x509_crldp_table ON bot_ca_table.bot_ca_subj_id=x509_crldp_table.bot_crldp_issuer_id) LEFT OUTER JOIN x509_ocsp_table ON bot_ca_table.bot_ca_subj_id=x509_ocsp_table.bot_ca_subj_id_fk) LEFT OUTER JOIN x509_ca_scope_table ON bot_ca_table.bot_ca_subj_id=x509_ca_scope_table.ca_subj_id_fk WHERE bot_ca_table.bot_ca_subj_id=? order by lower(bot_ca_table.x509ca_subj_dn)";

        String formatted = SqlFormatter.format(sql);

        assertTrue(formatted.contains("\n"), "La query dovrebbe essere resa multilinea");
        assertTrue(formatted.contains("bot_ca_table"));
    }

    @Test
    void leavesStandaloneTransactionCommandsUntouched() {
        assertEquals("BEGIN", SqlFormatter.format("BEGIN"));
        assertEquals("COMMIT", SqlFormatter.format("COMMIT"));
        assertEquals("ROLLBACK", SqlFormatter.format("ROLLBACK"));
    }

    @Test
    void leavesVeryLongQueryUntouched() {
        String sql = "SELECT * FROM permissions WHERE "
                + "permissions.name = 'admin' OR ".repeat(5_000)
                + "permissions.name = 'report'";

        assertEquals(SqlFormatter.format(sql), sql);
    }
}
