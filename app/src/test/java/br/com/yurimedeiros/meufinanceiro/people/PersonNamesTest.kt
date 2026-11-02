package br.com.yurimedeiros.meufinanceiro.people

import org.junit.Assert.*
import org.junit.Test

class PersonNamesTest {
    @Test fun displayCollapsesWhitespaceAndPreservesSpelling() {
        assertEquals("Pessoa Exemplo", PersonNames.display("  Pessoa  Exemplo  "))
    }
    @Test fun duplicateKeyIgnoresCaseAndSpacing() {
        assertEquals(PersonNames.key("Pessoa Exemplo"), PersonNames.key(" PESSOA   EXEMPLO "))
    }
    @Test fun distinctNamesRemainDistinct() {
        assertNotEquals(PersonNames.key("Ana"), PersonNames.key("Ana Maria"))
    }
}
