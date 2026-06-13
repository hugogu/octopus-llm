package com.octopusllm.testsupport

import com.octopusllm.auth.User
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ProviderResponse
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.Connection
import java.math.BigDecimal

object Feature005Fixtures {
    fun user(email: String = "feature005@example.com") =
        User(
            email = email,
            passwordHash = "hash",
            emailVerified = true,
            isActive = true,
        )

    fun connection(user: User = user()) = Feature003Fixtures.connection(user)

    fun configuredModel(
        user: User = user(),
        connection: Connection = connection(user),
    ) = ConfiguredModel(
        user = user,
        connection = connection,
        modelId = "feature-005-model",
        displayName = "Feature 005 Model",
        inputPricePerMtok = BigDecimal("1.2500"),
        outputPricePerMtok = BigDecimal("5.0000"),
        priceCurrency = "USD",
    )

    fun session(user: User = user()) = ChatSession(user = user, title = "Feature 005")

    fun turn(session: ChatSession = session()) =
        ChatTurn(
            session = session,
            sequenceNum = 1,
            promptText = "Hello",
            selectedModelIds = arrayOf("feature-005-model"),
        )

    fun response(
        turn: ChatTurn = turn(),
        model: ConfiguredModel = configuredModel(turn.session.user),
    ) = ProviderResponse(
        turn = turn,
        configuredModelId = model.id,
        modelId = model.modelId,
        modelDisplayName = model.displayName,
        protocol = model.connection.protocol,
        connectionId = model.connection.id,
        connectionLabel = model.connection.label,
        status = "complete",
        responseText = "World",
        inputTokens = 10,
        outputTokens = 20,
        latencyMs = 100,
        inputPricePerMtok = model.inputPricePerMtok,
        outputPricePerMtok = model.outputPricePerMtok,
        priceCurrency = model.priceCurrency,
    )
}
