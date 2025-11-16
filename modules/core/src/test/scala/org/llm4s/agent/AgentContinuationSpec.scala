package org.llm4s.agent

import org.llm4s.error.ValidationError
import org.llm4s.llmconnect.LLMClient
import org.llm4s.llmconnect.model._
import org.llm4s.toolapi.ToolRegistry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalamock.scalatest.MockFactory

/**
 * Test suite for Agent continuation methods (continueConversation and runMultiTurn).
 */
class AgentContinuationSpec extends AnyFlatSpec with Matchers with MockFactory {

  "Agent.continueConversation" should "add user message to previous completed state" in {
    val mockClient = mock[LLMClient]
    val agent = new Agent(mockClient)
    val tools = new ToolRegistry(Seq.empty)

    val completedState = AgentState(
      conversation = Conversation(Seq(UserMessage("First query"), AssistantMessage(Some("First response")))),
      tools = tools,
      initialQuery = Some("First query"),
      status = AgentStatus.Complete
    )

    // Mock the completion to return immediately
    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(AssistantMessage(Some("Second response")))))
      .once()

    val result = agent.continueConversation(completedState, "Second query")

    result shouldBe a[Right[_, _]]
    result.foreach { state =>
      state.conversation.messageCount should be >= 3
      val messages = state.conversation.messages
      // Should have original messages plus new user message
      messages.exists(m => m.role == MessageRole.User && m.content == "Second query") shouldBe true
    }
  }

  it should "fail if previous state is InProgress" in {
    val mockClient = mock[LLMClient]
    val agent = new Agent(mockClient)
    val tools = new ToolRegistry(Seq.empty)

    val inProgressState = AgentState(
      conversation = Conversation(Seq(UserMessage("Query"))),
      tools = tools,
      status = AgentStatus.InProgress
    )

    val result = agent.continueConversation(inProgressState, "Next query")

    result shouldBe a[Left[_, _]]
    result.left.foreach { error =>
      error shouldBe a[ValidationError]
      error.message should include("incomplete conversation")
    }
  }

  it should "fail if previous state is WaitingForTools" in {
    val mockClient = mock[LLMClient]
    val agent = new Agent(mockClient)
    val tools = new ToolRegistry(Seq.empty)

    val waitingState = AgentState(
      conversation = Conversation(Seq(UserMessage("Query"))),
      tools = tools,
      status = AgentStatus.WaitingForTools
    )

    val result = agent.continueConversation(waitingState, "Next query")

    result shouldBe a[Left[_, _]]
    result.left.foreach { error =>
      error shouldBe a[ValidationError]
    }
  }

  it should "accept previous state that is Failed" in {
    val mockClient = mock[LLMClient]
    val agent = new Agent(mockClient)
    val tools = new ToolRegistry(Seq.empty)

    val failedState = AgentState(
      conversation = Conversation(Seq(UserMessage("First query"))),
      tools = tools,
      status = AgentStatus.Failed("Some error")
    )

    // Mock completion
    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(AssistantMessage(Some("Response")))))
      .once()

    val result = agent.continueConversation(failedState, "Try again")

    result shouldBe a[Right[_, _]]
  }

  it should "reset logs for new turn" in {
    val mockClient = mock[LLMClient]
    val agent = new Agent(mockClient)
    val tools = new ToolRegistry(Seq.empty)

    val stateWithLogs = AgentState(
      conversation = Conversation(Seq(UserMessage("First query"))),
      tools = tools,
      status = AgentStatus.Complete,
      logs = Seq("Previous log 1", "Previous log 2")
    )

    // Mock completion
    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(AssistantMessage(Some("Response")))))
      .once()

    val result = agent.continueConversation(stateWithLogs, "Second query")

    result shouldBe a[Right[_, _]]
    result.foreach { state =>
      // Logs should be reset (or contain only new logs, not old ones)
      state.logs should not contain "Previous log 1"
      state.logs should not contain "Previous log 2"
    }
  }

  "Agent.runMultiTurn" should "execute all turns sequentially" in {
    val mockClient = mock[LLMClient]
    val agent = new Agent(mockClient)
    val tools = new ToolRegistry(Seq.empty)

    // Mock three completions (one for each turn)
    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(AssistantMessage(Some("Response 1")))))
      .once()

    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(AssistantMessage(Some("Response 2")))))
      .once()

    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(AssistantMessage(Some("Response 3")))))
      .once()

    val result = agent.runMultiTurn(
      initialQuery = "Query 1",
      followUpQueries = Seq("Query 2", "Query 3"),
      tools = tools
    )

    result shouldBe a[Right[_, _]]
    result.foreach { state =>
      state.status shouldBe AgentStatus.Complete
      // Should have at least 6 messages (3 user + 3 assistant)
      state.conversation.messageCount should be >= 6
    }
  }

  it should "fail on first error and not continue" in {
    val mockClient = mock[LLMClient]
    val agent = new Agent(mockClient)
    val tools = new ToolRegistry(Seq.empty)

    // First completion succeeds
    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(AssistantMessage(Some("Response 1")))))
      .once()

    // Second completion fails
    (mockClient.complete _)
      .expects(*, *)
      .returning(Left(ValidationError("API error")))
      .once()

    // Third should never be called

    val result = agent.runMultiTurn(
      initialQuery = "Query 1",
      followUpQueries = Seq("Query 2", "Query 3"),
      tools = tools
    )

    result shouldBe a[Left[_, _]]
  }

  it should "handle empty follow-up queries" in {
    val mockClient = mock[LLMClient]
    val agent = new Agent(mockClient)
    val tools = new ToolRegistry(Seq.empty)

    // Only one completion for initial query
    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(AssistantMessage(Some("Response")))))
      .once()

    val result = agent.runMultiTurn(
      initialQuery = "Single query",
      followUpQueries = Seq.empty,
      tools = tools
    )

    result shouldBe a[Right[_, _]]
    result.foreach { state =>
      state.status shouldBe AgentStatus.Complete
    }
  }
}
