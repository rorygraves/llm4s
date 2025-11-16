package org.llm4s.agent

import org.llm4s.llmconnect.model.Message

/**
 * Configuration for automatic context window management.
 *
 * Provides flexible strategies for pruning conversation history
 * to stay within token or message count limits.
 *
 * @param maxTokens Maximum number of tokens to keep (if specified, requires tokenCounter)
 * @param maxMessages Maximum number of messages to keep (simpler alternative to token-based)
 * @param preserveSystemMessage Always keep the system message (recommended)
 * @param minRecentTurns Minimum number of recent turns to preserve (even if limit exceeded)
 * @param pruningStrategy Strategy for pruning messages when limits are exceeded
 */
case class ContextWindowConfig(
  maxTokens: Option[Int] = None,
  maxMessages: Option[Int] = None,
  preserveSystemMessage: Boolean = true,
  minRecentTurns: Int = 3,
  pruningStrategy: PruningStrategy = PruningStrategy.OldestFirst
)

/**
 * Strategies for pruning conversation history when context limits are exceeded.
 */
sealed trait PruningStrategy

object PruningStrategy {

  /**
   * Remove oldest messages first (FIFO).
   * Preserves system message (if configured) and most recent messages.
   */
  case object OldestFirst extends PruningStrategy

  /**
   * Remove messages from the middle, keeping start and end.
   * Useful for preserving both initial context and recent exchanges.
   */
  case object MiddleOut extends PruningStrategy

  /**
   * Keep only the most recent N complete turns (user+assistant pairs).
   * Drops everything older than the specified number of turns.
   *
   * @param turns Number of recent turns to keep
   */
  case class RecentTurnsOnly(turns: Int) extends PruningStrategy

  /**
   * Custom pruning function.
   * Receives all messages and returns the subset to keep.
   * The function should be pure (no side effects) and deterministic.
   *
   * @param fn Function that takes messages and returns pruned messages
   */
  case class Custom(fn: Seq[Message] => Seq[Message]) extends PruningStrategy
}
