package org.llm4s.agent

import org.llm4s.llmconnect.model._
import org.llm4s.toolapi.ToolRegistry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Test suite for context window pruning strategies.
 */
class ContextWindowPruningSpec extends AnyFlatSpec with Matchers {

  "AgentState.pruneConversation" should "not prune when under limit" in {
    val config = ContextWindowConfig(maxMessages = Some(10))
    val messages = (1 to 5).map(i => UserMessage(s"Message $i"))
    val state = AgentState(
      conversation = Conversation(messages),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    pruned.conversation.messageCount shouldBe 5
    pruned.conversation.messages shouldBe messages
  }

  it should "prune oldest messages when limit exceeded" in {
    val config = ContextWindowConfig(maxMessages = Some(5))
    val messages = (1 to 10).map(i => UserMessage(s"Message $i"))
    val state = AgentState(
      conversation = Conversation(messages),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    pruned.conversation.messageCount shouldBe 5
    // Should keep the last 5 messages
    pruned.conversation.messages(0).content shouldBe "Message 6"
    pruned.conversation.messages(4).content shouldBe "Message 10"
  }

  it should "preserve system message when configured" in {
    val config = ContextWindowConfig(
      maxMessages = Some(5),
      preserveSystemMessage = true
    )
    val messages = Seq(SystemMessage("System")) ++ (1 to 10).map(i => UserMessage(s"Msg $i"))
    val state = AgentState(
      conversation = Conversation(messages),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    pruned.conversation.messageCount shouldBe 5
    pruned.conversation.messages.head.role shouldBe MessageRole.System
    pruned.conversation.messages.head.content shouldBe "System"
  }

  it should "not preserve system message when configured false" in {
    val config = ContextWindowConfig(
      maxMessages = Some(5),
      preserveSystemMessage = false
    )
    val messages = Seq(SystemMessage("System")) ++ (1 to 10).map(i => UserMessage(s"Msg $i"))
    val state = AgentState(
      conversation = Conversation(messages),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    pruned.conversation.messageCount shouldBe 5
    // System message might be dropped if it's old
    pruned.conversation.messages.exists(_.role == MessageRole.System) shouldBe false
  }

  it should "use MiddleOut strategy correctly" in {
    val config = ContextWindowConfig(
      maxMessages = Some(6),
      pruningStrategy = PruningStrategy.MiddleOut
    )
    val messages = (1 to 10).map(i => UserMessage(s"Message $i"))
    val state = AgentState(
      conversation = Conversation(messages),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    pruned.conversation.messageCount shouldBe 6
    // Should have first 3 and last 3
    pruned.conversation.messages(0).content shouldBe "Message 1"
    pruned.conversation.messages(1).content shouldBe "Message 2"
    pruned.conversation.messages(2).content shouldBe "Message 3"
    pruned.conversation.messages(3).content shouldBe "Message 8"
    pruned.conversation.messages(4).content shouldBe "Message 9"
    pruned.conversation.messages(5).content shouldBe "Message 10"
  }

  it should "use RecentTurnsOnly strategy correctly" in {
    val config = ContextWindowConfig(
      pruningStrategy = PruningStrategy.RecentTurnsOnly(2)
    )
    val messages = Seq(
      UserMessage("Turn 1 user"),
      AssistantMessage(Some("Turn 1 assistant")),
      UserMessage("Turn 2 user"),
      AssistantMessage(Some("Turn 2 assistant")),
      UserMessage("Turn 3 user"),
      AssistantMessage(Some("Turn 3 assistant"))
    )
    val state = AgentState(
      conversation = Conversation(messages),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    // Should keep last 2 turns (4 messages)
    pruned.conversation.messageCount shouldBe 4
    pruned.conversation.messages(0).content shouldBe "Turn 2 user"
    pruned.conversation.messages(3).content shouldBe "Turn 3 assistant"
  }

  it should "use custom pruning strategy" in {
    val customStrategy = PruningStrategy.Custom { messages =>
      // Keep only user messages
      messages.filter(_.role == MessageRole.User)
    }
    val config = ContextWindowConfig(pruningStrategy = customStrategy)

    val messages = Seq(
      UserMessage("User 1"),
      AssistantMessage(Some("Assistant 1")),
      UserMessage("User 2"),
      AssistantMessage(Some("Assistant 2"))
    )
    val state = AgentState(
      conversation = Conversation(messages),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    pruned.conversation.messageCount shouldBe 2
    pruned.conversation.messages.forall(_.role == MessageRole.User) shouldBe true
  }

  it should "use token-based pruning" in {
    val config = ContextWindowConfig(maxTokens = Some(20))

    // Create messages with known token counts
    val tokenCounter = (msg: Message) => msg.content.split("\\s+").length

    val messages = Seq(
      UserMessage("one two three four five"), // 5 tokens
      UserMessage("six seven eight"), // 3 tokens
      UserMessage("nine ten eleven twelve"), // 4 tokens
      UserMessage("thirteen fourteen fifteen") // 3 tokens
    )
    val state = AgentState(
      conversation = Conversation(messages),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config, tokenCounter)

    // Total is 15 tokens, under limit, so should not prune
    pruned.conversation.messageCount shouldBe 4

    // Now test with lower limit
    val config2 = ContextWindowConfig(maxTokens = Some(10))
    val pruned2 = AgentState.pruneConversation(state, config2, tokenCounter)

    // Should prune to fit within 10 tokens
    val totalTokens = pruned2.conversation.messages.map(tokenCounter).sum
    totalTokens should be <= 10
  }

  it should "handle edge case of empty conversation" in {
    val config = ContextWindowConfig(maxMessages = Some(5))
    val state = AgentState(
      conversation = Conversation(Seq.empty),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    pruned.conversation.messageCount shouldBe 0
  }

  it should "handle edge case of single message" in {
    val config = ContextWindowConfig(maxMessages = Some(5))
    val state = AgentState(
      conversation = Conversation(Seq(UserMessage("Only message"))),
      tools = new ToolRegistry(Seq.empty)
    )

    val pruned = AgentState.pruneConversation(state, config)

    pruned.conversation.messageCount shouldBe 1
    pruned.conversation.messages(0).content shouldBe "Only message"
  }
}
