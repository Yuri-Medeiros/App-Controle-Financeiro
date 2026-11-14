package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.assets.AssetKind
import br.com.yurimedeiros.meufinanceiro.assets.AssetPosition
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class VehicleValuationDetailsTest {
    private val vehicle = AssetPosition("moto", "Moto", AssetKind.VEHICLE, 1513700, 0, LocalDate.of(2026, 11, 2),
        quotedAt = "2026-11-09T01:45:00Z", quoteSource = "FIPE (Parallelum) · YAMAHA YS 150 FAZER SED/FLEX · 827088-0 · novembro de 2026",
        fipeReference = "2026-11")

    @Test fun separatesModelFromProviderCodeAndFormatsConsultationMetadata() {
        val details = VehicleValuationDetails.from(vehicle, ZoneId.of("America/Sao_Paulo"))
        assertEquals("YAMAHA YS 150 FAZER SED/FLEX", details.model)
        assertEquals("08/11/2026", details.updated)
        assertEquals("FIPE (Parallelum)", details.source)
        assertEquals("11/2026", details.reference)
        assertFalse(details.toString().contains("827088-0"))
    }

    @Test fun legacyOrUnavailableMetadataDoesNotShowRawSourceOrConfirmationAsUpdate() {
        val missing = VehicleValuationDetails.from(vehicle.copy(quoteSource = null, quotedAt = null, fipeReference = null))
        assertEquals("Moto", missing.model)
        assertEquals("Não disponível", missing.updated)
        assertEquals("Não disponível", missing.source)
        assertEquals("Não disponível", missing.reference)
        val malformed = VehicleValuationDetails.from(vehicle.copy(quoteSource = "827088-0", quotedAt = "bad", fipeReference = "bad"))
        assertEquals("Moto", malformed.model)
        assertEquals("Não disponível", malformed.updated)
        assertEquals("Não disponível", malformed.reference)
    }
}
