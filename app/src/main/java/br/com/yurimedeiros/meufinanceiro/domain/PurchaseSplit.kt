package br.com.yurimedeiros.meufinanceiro.domain

import java.math.BigDecimal
import java.math.BigInteger

/** Splits one purchase. List order must be the fixed participant order of that purchase. */
internal object PurchaseSplit {
    fun equally(totalCents: Long, participantCount: Int): List<Long> {
        require(participantCount > 0)
        return allocate(totalCents, List(participantCount) { BigInteger.ONE })
    }

    /** Exact decimal percentages, totaling 100. No floating point or implicit normalization. */
    fun byPercentages(totalCents: Long, percentages: List<BigDecimal>): List<Long> {
        require(percentages.isNotEmpty())
        require(percentages.all { it.signum() >= 0 })
        require(percentages.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal("100")) == 0)
        val scale = percentages.maxOf { it.scale() }.coerceAtLeast(0)
        return allocate(totalCents, percentages.map { it.setScale(scale).unscaledValue() })
    }

    private fun allocate(totalCents: Long, weights: List<BigInteger>): List<Long> {
        require(totalCents >= 0)
        val denominator = weights.fold(BigInteger.ZERO, BigInteger::add)
        val quotients = weights.map { BigInteger.valueOf(totalCents).multiply(it).divideAndRemainder(denominator) }
        val result = quotients.map { it[0].longValueExact() }.toMutableList()
        val remaining = (totalCents - result.sum()).toInt()
        val order = weights.indices.sortedWith(compareByDescending<Int> { quotients[it][1] }.thenBy { it })
        order.take(remaining).forEach { result[it] = result[it] + 1 }
        return result
    }
}
