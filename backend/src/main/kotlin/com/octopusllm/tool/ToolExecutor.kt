package com.octopusllm.tool

import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

/**
 * Runs tools off the event loop with a bounded timeout + one retry, and deduplicates identical
 * invocations within a turn (feature 009, FR-007/FR-008). A [TurnScope] shares a single execution
 * across every model that requests the same tool with the same arguments, so external cost/latency is
 * not multiplied; each requesting model still gets its own [callId]-tagged event stream.
 *
 * Retry policy: a thrown exception or timeout is retried once with short backoff. A [ToolResult.Failure]
 * *returned* by a tool is a normal signal and is not retried — tools that expect transient failures
 * should throw so the retry applies.
 */
@Component
class ToolExecutor(
    // Defaults encode the FR-007 policy (15s timeout, one backed-off retry). Spring honors Kotlin
    // default parameters when no matching bean exists; tests override them for fast, deterministic runs.
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val retryBackoff: Duration = DEFAULT_RETRY_BACKOFF,
    private val maxRetries: Long = DEFAULT_MAX_RETRIES,
) {

    companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(15)
        val DEFAULT_RETRY_BACKOFF: Duration = Duration.ofMillis(250)
        const val DEFAULT_MAX_RETRIES = 1L
    }

    /** Per-turn deduplication cache. Create one per turn via [newTurnScope]; not shared across turns. */
    class TurnScope {
        private val inflight = ConcurrentHashMap<String, Mono<ToolResult>>()

        internal fun resultFor(
            toolName: String,
            arguments: Map<String, Any?>,
            supplier: () -> Mono<ToolResult>,
        ): Mono<ToolResult> {
            val key = "$toolName:${ToolArguments.hash(arguments)}"
            // computeIfAbsent + cache(): the first caller triggers execution, everyone shares the result.
            return inflight.computeIfAbsent(key) { supplier().cache() }
        }
    }

    fun newTurnScope(): TurnScope = TurnScope()

    /**
     * Executes [toolName] once per turn per unique argument set, returning the shared [ToolResult].
     * Never signals an error — failures and timeouts are folded into [ToolResult.Failure].
     */
    fun executeOnce(scope: TurnScope, tool: Tool, arguments: Map<String, Any?>): Mono<ToolResult> =
        scope.resultFor(tool.definition.name, arguments) { runWithPolicy(tool, arguments) }

    /**
     * Executes and adapts the outcome into the provider-independent event stream the tool loop consumes:
     * a `RUNNING` status, then the result, then the terminal status. Deduplicated per turn like
     * [executeOnce], but each call gets its own [callId] so per-model lineage is preserved.
     */
    fun execute(
        scope: TurnScope,
        callId: String,
        tool: Tool,
        arguments: Map<String, Any?>,
    ): Flux<UnifiedInteractionEvent> {
        val toolName = tool.definition.name
        val running: UnifiedInteractionEvent =
            UnifiedInteractionEvent.ToolStatus(callId, toolName, ToolInvocationStatus.RUNNING)
        return Flux.concat(
            Flux.just(running),
            executeOnce(scope, tool, arguments).flatMapMany { result ->
                Flux.just(
                    UnifiedInteractionEvent.ToolResultEvent(callId, toolName, result),
                    UnifiedInteractionEvent.ToolStatus(callId, toolName, result.status),
                )
            },
        )
    }

    private fun runWithPolicy(tool: Tool, arguments: Map<String, Any?>): Mono<ToolResult> =
        Mono.fromCallable { tool.execute(arguments) }
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(timeout)
            .retryWhen(Retry.backoff(maxRetries, retryBackoff))
            .onErrorResume { error -> Mono.just(failureFor(error)) }

    private fun failureFor(error: Throwable): ToolResult.Failure {
        // retryWhen wraps the last failure in a RetryExhaustedException, so walk the cause chain.
        val causes = generateSequence(error) { it.cause }
        if (causes.any { it is TimeoutException }) {
            return ToolResult.Failure("Tool timed out after ${timeout.seconds}s", timedOut = true)
        }
        val root = causes.last()
        return ToolResult.Failure(root.message ?: "Tool execution failed")
    }
}
