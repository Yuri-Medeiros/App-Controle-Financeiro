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
    @Test fun fallsBackToDocumentedV1AcrossTheWholeSelectionAndPriceFlow() {
        val cases = listOf(
            Triple("motorcycles/brands", "motos/marcas", """[{"codigo":"80","nome":"Honda"}]"""),
            Triple("motorcycles/brands/80/models", "motos/marcas/80/modelos", """{"modelos":[{"codigo":1234,"nome":"Modelo de teste"}],"anos":[]}"""),
            Triple("motorcycles/brands/80/models/1234/years", "motos/marcas/80/modelos/1234/anos", """[{"codigo":"2020-1","nome":"2020 Gasolina"}]""")
        )
        for ((path, legacyPath, body) in cases) {
            val urls = mutableListOf<String>()
            val result = FipeQuotes.catalog(path) { url ->
                urls += url
                if (urls.size == 1) throw java.net.UnknownHostException("v2 unavailable")
                assertEquals("https://parallelum.com.br/fipe/api/v1/$legacyPath", url)
                body
            }
            assertEquals(2, urls.size)
            assertEquals(1, result.size)
        }
        var calls = 0
        val quote = FipeQuotes.fetch(vehicle.path, today) { url ->
            if (++calls == 1) throw QuoteHttpException(503)
            assertEquals("https://parallelum.com.br/fipe/api/v1/${vehicle.path}", url)
            """{"TipoVeiculo":2,"Valor":"R$ 15.432,10","Marca":"Honda","Modelo":"Modelo de teste","AnoModelo":2020,"SiglaCombustivel":"G","CodigoFipe":"811000-1","MesReferencia":"novembro de 2026"}"""
        }
        assertEquals(1543210L, quote.cents)
        assertEquals(vehicle.path, quote.vehiclePath)
        assertEquals(2, calls)
    }

    @Test fun neverRetriesDenialsQuotaOrBeyondTheSecondEndpoint() {
        for (status in listOf(401, 403, 404, 429)) {
            var calls = 0
            val failure = runCatching { FipeQuotes.catalog("motorcycles/brands") {
                calls++; throw QuoteHttpException(status)
            } }.exceptionOrNull()
            assertEquals(1, calls)
            assertEquals(status, (failure as QuoteHttpException).status)
        }
        var calls = 0
        assertTrue(runCatching { FipeQuotes.catalog("motorcycles/brands") {
            calls++; "not json"
        } }.isFailure)
        assertEquals(2, calls)
        calls = 0
        val failure = runCatching { FipeQuotes.catalog("motorcycles/brands") {
            calls++; throw java.net.SocketTimeoutException()
        } }.exceptionOrNull()
        assertEquals(2, calls)
        assertTrue(failure is java.net.SocketTimeoutException)
    }

    @Test fun catalogAcceptsNumericCodesAndErrorsIdentifyTheActualFailure() {
        assertEquals("80", FipeQuotes.catalog("motorcycles/brands") { """[{"code":80,"name":"Honda"}]""" }.single().code)
        assertTrue(FipeQuotes.failure(QuoteHttpException(403)).contains("HTTP 403"))
        assertTrue(FipeQuotes.failure(java.net.UnknownHostException()).contains("DNS"))
        assertTrue(FipeQuotes.failure(java.net.SocketTimeoutException()).contains("demorou"))
        assertTrue(FipeQuotes.failure(javax.net.ssl.SSLException("TLS")).contains("segura"))
        assertTrue(FipeQuotes.failure(org.json.JSONException("bad payload")).contains("JSON fora do formato"))
    }

    @Test fun invalidPrimaryJsonTriggersAlternativeAndReturnsOnlyValidatedCatalog() {
        val replies = listOf("not json", "{}", "<html>unavailable</html>")
        for (reply in replies) {
            val urls = mutableListOf<String>()
            val result = FipeQuotes.catalog("motorcycles/brands") { url ->
                urls += url
                if (urls.size == 1) reply else """[{"codigo":"80","nome":"Honda"}]"""
            }
            assertEquals(listOf(FipeOption("80", "Honda")), result)
            assertEquals(listOf("${FipeQuotes.BASE}/motorcycles/brands", "https://parallelum.com.br/fipe/api/v1/motos/marcas"), urls)
        }
    }

    @Test fun htmlDetectedByHttpTransportAlsoTriggersAlternative() {
        var calls = 0
        val result = FipeQuotes.catalog("motorcycles/brands") {
            if (++calls == 1) throw QuoteResponseException("serviço devolveu página HTML em vez da cotação")
            """[{"codigo":"80","nome":"Honda"}]"""
        }
        assertEquals("Honda", result.single().name)
        assertEquals(2, calls)
        assertTrue(FipeQuotes.failure(QuoteResponseException("serviço devolveu página HTML em vez da cotação")).contains("HTML"))
    }

    @Test fun redirectsAndInvalidFinancialValuesAreNotRetriedAndBomIsAccepted() {
        var calls = 0
        assertTrue(runCatching { FipeQuotes.catalog("motorcycles/brands") {
            calls++; throw QuoteResponseException("destino de redirecionamento não permitido")
        } }.isFailure)
        assertEquals(1, calls)
        calls = 0
        assertTrue(runCatching { FipeQuotes.fetch(vehicle.path, today) {
            calls++; json().put("price", "R$ 0,00").toString()
        } }.isFailure)
        assertEquals(1, calls)
        assertEquals("80", FipeQuotes.catalog("motorcycles/brands") {
            "\uFEFF" + """[{"code":"80","name":"Honda"}]"""
        }.single().code)
    }

    @Test fun newVehiclesStartWithFipeValueAndCanOnlyBeSoldWhileLegacyEventsStillReplay() {
        val registration = FipeQuotes.registration("new", "Minha moto", quote(), today)
        AssetRules.validateNew(AssetBook(), registration)
        val book = AssetRules.apply(AssetBook(), registration)
        val saved = book.assets.single()
        assertEquals(1543210L, saved.cents)
        assertEquals(vehicle.path, saved.vehiclePath)
        assertEquals("2026-11", saved.fipeReference)
        assertEquals(now.toString(), saved.quotedAt)
        assertEquals(registration, AssetCodec.decode(AssetCodec.encode(registration)))
        val confirmation = AssetChange.Confirm(saved.id, 2000000, 0, today)
        val contribution = AssetChange.Transfer(saved.id, 42, AssetTransferKind.CONTRIBUTION, 10000, 0, 0, today, false)
        for (change in listOf(confirmation, contribution, AssetChange.Register(asset()),
            AssetChange.Configure(saved.id, "", 0, false),
            AssetChange.Quote(saved.id, 10000, null, now.toString(), "manual"))) {
            assertTrue(runCatching { AssetRules.validateNew(book, change) }.isFailure)
        }
        // Historical manual confirmations and contributions remain readable during replay.
        assertEquals(2000000L, AssetRules.apply(book, confirmation).assets.single().cents)
        assertEquals(1553210L, AssetRules.apply(book, contribution).assets.single().cents)
        val sale = contribution.copy(kind = AssetTransferKind.SALE)
        AssetRules.validateNew(book, sale)
        val sold = AssetRules.apply(book, sale)
        assertTrue(sold.assets.single().disposed)
        assertEquals(0L, sold.assets.single().valueCents)
        assertEquals(2, sold.history.size)
        assertTrue(runCatching { FipeQuotes.registration("x", "Moto", quote().copy(reference = YearMonth.of(2026, 10)), today) }.isFailure)
    }

    @Test fun manualRefreshIncludesAlreadyQuotedVehiclesWithoutRefreshingSoldOrUnconfiguredAssets() {
        val saved = asset().copy(fipeReference = "2026-11")
        assertFalse(FipeQuotes.due(saved, today))
        assertTrue(FipeQuotes.due(saved, today, manual = true))
        assertFalse(FipeQuotes.due(saved.copy(disposed = true), today, manual = true))
        assertFalse(FipeQuotes.due(saved.copy(vehiclePath = ""), today, manual = true))
        assertFalse(FipeQuotes.due(saved.copy(kind = AssetKind.INVESTMENT), today, manual = true))
    }

    @Test fun renamingVehiclePreservesFipeValuationHistoryAndLegacyConfiguration() {
        val original = asset()
        val quoted = AssetRules.apply(AssetBook(listOf(original)), FipeQuotes.action(original, original, quote(), today))
        val before = quoted.assets.single()
        val rename = AssetChange.Configure(before.id, before.vehiclePath, before.monthlyFgts, before.fgtsDepositsActive, "  Minha Fazer  ")
        assertEquals(rename, AssetCodec.decode(AssetCodec.encode(rename)))
        val renamed = AssetRules.apply(quoted, rename)
        assertEquals(before.copy(name = "Minha Fazer"), renamed.assets.single())
        assertEquals(quoted.history + rename, renamed.history)
        assertTrue(runCatching { AssetRules.apply(quoted, rename.copy(name = "  ")) }.isFailure)
        val legacyJson = AssetCodec.encode(rename).apply { remove("name") }
        val legacy = AssetCodec.decode(legacyJson) as AssetChange.Configure
        assertNull(legacy.name)
        assertEquals(before, AssetRules.apply(quoted, legacy).assets.single())
        val newIdentity = rename.copy(vehiclePath = "motos/marcas/80/modelos/999/anos/2020-1")
        val reconfigured = AssetRules.apply(quoted, newIdentity).assets.single()
        assertEquals("Minha Fazer", reconfigured.name)
        assertEquals(before.cents, reconfigured.cents)
        assertNull(reconfigured.fipeReference)
        assertNull(reconfigured.quotedAt)
    }

}
