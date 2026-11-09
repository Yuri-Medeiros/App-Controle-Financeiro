package br.com.yurimedeiros.meufinanceiro.assets

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class FipeQuotesTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val vehicle = FipeVehicle(FipeType.MOTORCYCLE, "80", "1234", "2020-1")
    private fun json() = JSONObject("""{"vehicleType":2,"price":"R$ 15.432,10","brand":"Honda","model":"Modelo de teste","modelYear":2020,"fuel":"Gasolina","fuelAcronym":"G","codeFipe":"811000-1","referenceMonth":"novembro de 2026"}""")
    private fun asset(path: String = vehicle.path) = AssetPosition("moto", "Moto", AssetKind.VEHICLE, 1500000, 0, today.minusDays(7), vehiclePath = path)
    private fun quote() = FipeQuotes.parse(json().toString(), vehicle, today, now)

    @Test fun parsesBrlPriceExactlyAndMapsStoredPortuguesePathToV2() {
        assertEquals(vehicle, FipeVehicle.parse(vehicle.path))
        val quote = FipeQuotes.fetch(vehicle.path, today) { url ->
            assertEquals("https://fipe.parallelum.com.br/api/v2/motorcycles/brands/80/models/1234/years/2020-1", url)
            json().toString()
        }
        assertEquals(1543210L, quote.cents)
        assertEquals(YearMonth.of(2026, 11), quote.reference)
        assertTrue(quote.source.contains("811000-1"))
        assertNull(FipeVehicle.parse("https://evil.example/vehicle"))
        assertNull(FipeVehicle.parse("motos/marcas/80/modelos/../anos/2020-1"))
    }
    @Test fun rejectsWrongVehicleFuelReferenceAndInvalidValues() {
        for ((field, value) in listOf("vehicleType" to 1, "vehicleType" to 2.5, "modelYear" to 2021, "modelYear" to 2020.5, "brand" to JSONObject.NULL, "model" to JSONObject.NULL, "fuelAcronym" to "D",
            "referenceMonth" to "outubro de 2026", "referenceMonth" to "dezembro de 2026", "referenceMonth" to "invalid",
            "price" to "R$ 0,00", "price" to "USD 1.000,00", "price" to "R$ 15.432,109", "codeFipe" to "wrong")) {
            assertTrue("$field=$value", runCatching { FipeQuotes.parse(json().put(field, value).toString(), vehicle, today, now) }.isFailure)
        }
    }
    @Test fun catalogRejectsUntrustedCodesAndSupportsZeroKmWithoutGuessingFuel() {
        val rows = FipeQuotes.catalog("motorcycles/brands/80/models/1234/years") {
            """[{"code":"2020-1","name":"2020 Gasolina"},{"code":"32000-1","name":"Zero KM Gasolina"}]"""
        }
        assertEquals(2, rows.size)
        assertNotNull(FipeVehicle.parse("motos/marcas/80/modelos/1234/anos/32000-1"))
        assertTrue(runCatching { FipeQuotes.catalog("motorcycles/brands") { """[{"code":"../evil","name":"bad"}]""" } }.isFailure)
        assertTrue(runCatching { FipeQuotes.catalog("../outside") { "[]" } }.isFailure)
        assertEquals("citroen", FipeQuotes.normalize("Citroën"))
    }
    @Test fun monthlyCacheAndReconfigurationPreserveLastValueAndInvalidateIdentity() {
        val original = asset()
        assertTrue(FipeQuotes.due(original, today))
        val change = FipeQuotes.action(original, original, quote(), today)
        var book = AssetRules.apply(AssetBook(listOf(original)), change)
        val saved = book.assets.single()
        assertEquals(1543210L, saved.cents)
        assertEquals("2026-11", saved.fipeReference)
        assertFalse(FipeQuotes.due(saved, today))
        assertTrue(FipeQuotes.due(saved, today.plusMonths(1)))
        book = AssetRules.apply(book, AssetChange.Configure(saved.id, "motos/marcas/80/modelos/999/anos/2020-1", 0, false))
        assertEquals(saved.cents, book.assets.single().cents)
        assertNull(book.assets.single().fipeReference)
        assertNull(book.assets.single().quoteSource)
        assertTrue(FipeQuotes.due(book.assets.single(), today))
    }
    @Test fun staleInFlightQuoteCannotOverwriteEditedSoldOrManuallyConfirmedVehicle() {
        val original = asset()
        for (changed in listOf(original.copy(vehiclePath = ""), original.copy(disposed = true), original.copy(cents = 999))) {
            assertTrue(runCatching { FipeQuotes.action(original, changed, quote(), today) }.isFailure)
        }
        val future = original.copy(confirmedDate = today.plusDays(1))
        assertTrue(runCatching { FipeQuotes.action(future, future, quote(), today) }.isFailure)
        assertFalse(FipeQuotes.due(original.copy(disposed = true), today))
        assertFalse(FipeQuotes.due(original.copy(vehiclePath = ""), today))
        assertTrue(runCatching { FipeQuotes.action(original, original, quote(), today.plusMonths(1)) }.isFailure)
    }
    @Test fun quoteCodecRetainsReferenceAndLegacyEventsRemainReadable() {
        val original = asset()
        val action = FipeQuotes.action(original, original, quote(), today)
        assertEquals(action, AssetCodec.decode(AssetCodec.encode(action)))
        val saved = AssetRules.apply(AssetBook(listOf(original)), action).assets.single()
        assertEquals(saved, (AssetCodec.decode(AssetCodec.encode(AssetChange.Register(saved))) as AssetChange.Register).asset)
        val legacy = AssetCodec.encode(action).apply { remove("fipeReference"); remove("vehiclePath") }
        val decoded = AssetCodec.decode(legacy) as AssetChange.Quote
        assertNull(decoded.fipeReference)
        val oldBook = AssetRules.apply(AssetBook(listOf(original)), decoded)
        assertEquals(action.cents, oldBook.assets.single().cents)
        val register = AssetCodec.encode(AssetChange.Register(original))
        register.getJSONObject("asset").remove("fipeReference")
        assertEquals(original, (AssetCodec.decode(register) as AssetChange.Register).asset)
    }
    @Test fun mismatchedIdentityAndIncompleteMetadataCannotEnterTheLedger() {
        val original = asset()
        val change = FipeQuotes.action(original, original, quote(), today)
        assertTrue(runCatching { AssetRules.apply(AssetBook(listOf(original)), change.copy(vehiclePath = "motos/marcas/80/modelos/999/anos/2020-1")) }.isFailure)
        assertTrue(runCatching { AssetRules.apply(AssetBook(listOf(original)), change.copy(vehiclePath = null)) }.isFailure)
        val saved = AssetRules.apply(AssetBook(listOf(original)), change).assets.single()
        assertTrue(runCatching { AssetRules.validateNew(AssetBook(listOf(saved)), change.copy(timestamp = now.minusSeconds(60).toString())) }.isFailure)
    }

    @Test fun vehicleOperationsInvalidateOnlyValuationsTheyActuallyChange() {
        val original = asset()
        val quoted = AssetRules.apply(AssetBook(listOf(original)), FipeQuotes.action(original, original, quote(), today))
        val contribution = AssetRules.apply(quoted, AssetChange.Transfer(original.id, 1, AssetTransferKind.CONTRIBUTION, 10000, 0, 0, today, false))
        assertEquals(1553210L, contribution.assets.single().cents)
        assertNull(contribution.assets.single().fipeReference)
        assertNull(contribution.assets.single().quoteSource)
        assertTrue(FipeQuotes.due(contribution.assets.single(), today))
        val included = AssetRules.apply(quoted, AssetChange.Transfer(original.id, 1, AssetTransferKind.CONTRIBUTION, 10000, 0, 0, today, true))
        assertEquals(quoted.assets.single(), included.assets.single())
        val sold = AssetRules.apply(quoted, AssetChange.Transfer(original.id, 1, AssetTransferKind.SALE, 1500000, 0, 0, today, false))
        assertEquals(0L, sold.assets.single().valueCents)
        assertNull(sold.assets.single().fipeReference)
        assertFalse(FipeQuotes.due(sold.assets.single(), today))
        assertTrue(sold.history.any { it is AssetChange.Quote && it.cents == 1543210L })
    }

    @Test fun manualValueIsNotMisrepresentedAsTheCachedFipeQuote() {
        val original = asset()
        var book = AssetRules.apply(AssetBook(listOf(original)), FipeQuotes.action(original, original, quote(), today))
        book = AssetRules.apply(book, AssetChange.Confirm(original.id, 2000000, 0, today))
        assertEquals(2000000L, book.assets.single().cents)
        assertNull(book.assets.single().fipeReference)
        assertNull(book.assets.single().quoteSource)
    }
}
