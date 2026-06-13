package com.octopusllm.analytics

import java.math.BigDecimal
import java.math.RoundingMode

data class EstimatedCost(val amount: BigDecimal, val currency: String)

object ModelPricing {
    fun estimate(
        inputTokens: Int?,
        outputTokens: Int?,
        inputPricePerMtok: BigDecimal?,
        outputPricePerMtok: BigDecimal?,
        currency: String?,
    ): EstimatedCost? {
        if (currency == null || inputTokens == null || outputTokens == null) return null
        if (inputPricePerMtok == null || outputPricePerMtok == null) return null
        val divisor = BigDecimal("1000000")
        val amount = BigDecimal(inputTokens).multiply(inputPricePerMtok)
            .add(BigDecimal(outputTokens).multiply(outputPricePerMtok))
            .divide(divisor, 8, RoundingMode.HALF_UP)
        return EstimatedCost(amount, currency)
    }
}
