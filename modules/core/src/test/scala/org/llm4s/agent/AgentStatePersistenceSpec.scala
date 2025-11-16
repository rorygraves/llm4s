package org.llm4s.agent

import org.llm4s.llmconnect.model._
import org.llm4s.toolapi.ToolRegistry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{ Files, Paths }

/**
 * Test suite for AgentState persistence (JSON serialization/deserialization).
 */
class AgentStatePersistenceSpec extends AnyFlatSpec with Matchers {

  "AgentState.toJson and fromJson" should "round-trip correctly" in {
    val tools = new ToolRegistry(Seq.empty)
    val state = AgentState(
      conversation = Conversation(
        Seq(
          UserMessage("Hello"),
          AssistantMessage(Some("Hi there"))
        )
      ),
      tools = tools,
      initialQuery = Some("Hello"),
      status = AgentStatus.Complete,
      logs = Seq("Log 1", "Log 2"),
      systemMessage = Some(SystemMessage("You are helpful")),
      completionOptions = CompletionOptions(temperature = Some(0.7))
    )

    val json = AgentState.toJson(state)
    val loaded = AgentState.fromJson(json, tools)

    loaded shouldBe a[Right[_, _]]
    loaded.foreach { loadedState =>
      loadedState.conversation.messageCount shouldBe state.conversation.messageCount
      loadedState.initialQuery shouldBe state.initialQuery
      loadedState.status shouldBe state.status
      loadedState.logs shouldBe state.logs
      loadedState.systemMessage.map(_.content) shouldBe state.systemMessage.map(_.content)
      loadedState.completionOptions.temperature shouldBe state.completionOptions.temperature
    }
  }

  it should "handle missing optional fields" in {
    val tools = new ToolRegistry(Seq.empty)
    val state = AgentState(
      conversation = Conversation(Seq(UserMessage("Test"))),
      tools = tools,
      initialQuery = None,
      systemMessage = None
    )

    val json = AgentState.toJson(state)
    val loaded = AgentState.fromJson(json, tools)

    loaded shouldBe a[Right[_, _]]
    loaded.foreach { loadedState =>
      loadedState.initialQuery shouldBe None
      loadedState.systemMessage shouldBe None
    }
  }

  it should "preserve conversation with tool messages" in {
    val tools = new ToolRegistry(Seq.empty)
    val state = AgentState(
      conversation = Conversation(
        Seq(
          UserMessage("Use a tool"),
          AssistantMessage(Some("OK"), toolCalls = Seq(ToolCall("tool1", "id1", "{}"))),
          ToolMessage("{\"result\": \"success\"}", "id1"),
          AssistantMessage(Some("Done"))
        )
      ),
      tools = tools
    )

    val json = AgentState.toJson(state)
    val loaded = AgentState.fromJson(json, tools)

    loaded shouldBe a[Right[_, _]]
    loaded.foreach { loadedState =>
      loadedState.conversation.messageCount shouldBe 4
      val messages = loadedState.conversation.messages
      messages(0).role shouldBe MessageRole.User
      messages(1).role shouldBe MessageRole.Assistant
      messages(2).role shouldBe MessageRole.Tool
      messages(3).role shouldBe MessageRole.Assistant
    }
  }

  it should "preserve different status types" in {
    val tools = new ToolRegistry(Seq.empty)

    val statuses = Seq(
      AgentStatus.InProgress,
      AgentStatus.WaitingForTools,
      AgentStatus.Complete,
      AgentStatus.Failed("Error message")
    )

    statuses.foreach { status =>
      val state = AgentState(
        conversation = Conversation(Seq(UserMessage("Test"))),
        tools = tools,
        status = status
      )

      val json = AgentState.toJson(state)
      val loaded = AgentState.fromJson(json, tools)

      loaded shouldBe a[Right[_, _]]
      loaded.foreach { loadedState =>
        loadedState.status shouldBe status
      }
    }
  }

  "AgentState.saveToFile and loadFromFile" should "persist to disk" in {
    val tempFile = Files.createTempFile("agent-state-test", ".json")
    try {
      val tools = new ToolRegistry(Seq.empty)
      val state = AgentState(
        conversation = Conversation(
          Seq(
            UserMessage("Question?"),
            AssistantMessage(Some("Answer!"))
          )
        ),
        tools = tools,
        initialQuery = Some("Question?"),
        status = AgentStatus.Complete
      )

      val saveResult = AgentState.saveToFile(state, tempFile.toString)
      saveResult shouldBe a[Right[_, _]]

      // Verify file exists
      Files.exists(tempFile) shouldBe true

      // Load it back
      val loadResult = AgentState.loadFromFile(tempFile.toString, tools)
      loadResult shouldBe a[Right[_, _]]

      loadResult.foreach { loaded =>
        loaded.conversation.messageCount shouldBe state.conversation.messageCount
        loaded.initialQuery shouldBe state.initialQuery
        loaded.status shouldBe state.status
      }
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "handle missing file" in {
    val tools = new ToolRegistry(Seq.empty)
    val result = AgentState.loadFromFile("/nonexistent/path/file.json", tools)

    result shouldBe a[Left[_, _]]
  }

  it should "handle corrupted JSON" in {
    val tempFile = Files.createTempFile("agent-state-corrupt", ".json")
    try {
      import java.nio.charset.StandardCharsets
      Files.write(tempFile, "{ invalid json }".getBytes(StandardCharsets.UTF_8))

      val tools = new ToolRegistry(Seq.empty)
      val result = AgentState.loadFromFile(tempFile.toString, tools)

      result shouldBe a[Left[_, _]]
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "preserve all conversation details" in {
    val tempFile = Files.createTempFile("agent-state-full", ".json")
    try {
      val tools = new ToolRegistry(Seq.empty)
      val state = AgentState(
        conversation = Conversation(
          Seq(
            UserMessage("First"),
            AssistantMessage(Some("First response")),
            UserMessage("Second"),
            AssistantMessage(Some("Second response"))
          )
        ),
        tools = tools,
        initialQuery = Some("First"),
        status = AgentStatus.Complete,
        logs = Seq("Step 1", "Step 2", "Step 3"),
        systemMessage = Some(SystemMessage("Be helpful")),
        completionOptions = CompletionOptions(
          temperature = Some(0.8),
          maxTokens = Some(1000)
        )
      )

      AgentState.saveToFile(state, tempFile.toString) shouldBe a[Right[_, _]]

      val loaded = AgentState.loadFromFile(tempFile.toString, tools)
      loaded shouldBe a[Right[_, _]]

      loaded.foreach { s =>
        s.conversation.messageCount shouldBe 4
        s.initialQuery shouldBe Some("First")
        s.logs shouldBe Seq("Step 1", "Step 2", "Step 3")
        s.systemMessage.map(_.content) shouldBe Some("Be helpful")
        s.completionOptions.temperature shouldBe Some(0.8)
        s.completionOptions.maxTokens shouldBe Some(1000)
      }
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }
}
