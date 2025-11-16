package org.llm4s.agent

import org.llm4s.agent.guardrails.{ CompositeGuardrail, InputGuardrail, OutputGuardrail }
import org.llm4s.core.safety.Safety
import org.llm4s.llmconnect.LLMClient
import org.llm4s.llmconnect.model._
import org.llm4s.toolapi._
import org.llm4s.types.Result
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

/**
 * Basic Agent implementation.
 */
class Agent(client: LLMClient) {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Initializes a new agent state with the given query
   *
   * @param query The user query to process
   * @param tools The registry of available tools
   * @param systemPromptAddition Optional additional text to append to the default system prompt
   * @param completionOptions Optional completion options for LLM calls (temperature, maxTokens, etc.)
   * @return A new AgentState initialized with the query and tools
   */
  def initialize(
    query: String,
    tools: ToolRegistry,
    systemPromptAddition: Option[String] = None,
    completionOptions: CompletionOptions = CompletionOptions()
  ): AgentState = {
    val baseSystemPrompt = """You are a helpful assistant with access to tools. 
        |Follow these steps:
        |1. Analyze the user's question and determine which tools you need to use
        |2. Use tools ONE AT A TIME - make one tool call, wait for the result, then decide if you need more tools
        |3. Use the results from previous tool calls in subsequent tool calls when needed
        |4. When you have enough information, provide a helpful final answer
        |5. Think step by step and be thorough""".stripMargin

    val fullSystemPrompt = systemPromptAddition match {
      case Some(addition) => s"$baseSystemPrompt\n\n$addition"
      case None           => baseSystemPrompt
    }

    val systemMsg = SystemMessage(fullSystemPrompt)
    // Only store user message in conversation - system message is now config, not history
    val initialMessages = Seq(
      UserMessage(query)
    )

    AgentState(
      conversation = Conversation(initialMessages),
      tools = tools,
      initialQuery = Some(query),
      systemMessage = Some(systemMsg),
      completionOptions = completionOptions
    )
  }

  /**
   * Runs a single step of the agent's reasoning process
   */
  def runStep(state: AgentState, debug: Boolean = false): Result[AgentState] =
    state.status match {
      case AgentStatus.InProgress =>
        // Get tools from registry and merge with completion options from state
        val options = state.completionOptions.copy(tools = state.tools.tools)

        if (debug) {
          logger.info("[DEBUG] Running completion step")
          logger.info("[DEBUG] Status: InProgress -> requesting LLM completion")
          logger.info("[DEBUG] Available tools: {}", state.tools.tools.map(_.name).mkString(", "))
          logger.info("[DEBUG] Conversation history: {} messages", state.conversation.messages.size)
        } else {
          logger.debug("Running completion step with tools: {}", state.tools.tools.map(_.name).mkString(", "))
        }

        // Request next step from LLM using system message injection
        client.complete(state.toApiConversation, options) match {
          case Right(completion) =>
            val logMessage = completion.message.toolCalls match {
              case Seq() => s"[assistant] text: ${completion.message.content}"
              case toolCalls =>
                val toolNames = toolCalls.map(_.name).mkString(", ")
                s"[assistant] tools: ${toolCalls.size} tool calls requested ($toolNames)"
            }

            if (debug) {
              logger.info("[DEBUG] LLM response received")
              logger.info(
                "[DEBUG] Response type: {}",
                if (completion.message.toolCalls.isEmpty) "text" else "tool_calls"
              )
              if (completion.message.content != null && completion.message.content.nonEmpty) {
                logger.info("[DEBUG] Response content: {}", completion.message.content)
              }
              if (completion.message.toolCalls.nonEmpty) {
                logger.info("[DEBUG] Tool calls requested: {}", completion.message.toolCalls.size)
                completion.message.toolCalls.foreach { tc =>
                  logger.info("[DEBUG]   - Tool: {}", tc.name)
                  logger.info("[DEBUG]     ID: {}", tc.id)
                  logger.info("[DEBUG]     Arguments (raw): {}", tc.arguments)
                  logger.info("[DEBUG]     Arguments type: {}", tc.arguments.getClass.getSimpleName)
                }
              }
            }

            val updatedState = state
              .log(logMessage)
              .addMessage(completion.message)

            completion.message.toolCalls match {
              case Seq() =>
                // No tool calls - agent is ready to answer
                if (debug) {
                  logger.info("[DEBUG] Status: InProgress -> Complete (no tool calls)")
                }
                Right(updatedState.withStatus(AgentStatus.Complete))

              case _ =>
                // Don't process tools yet, just mark as waiting
                if (debug) {
                  logger.info("[DEBUG] Status: InProgress -> WaitingForTools")
                } else {
                  logger.debug("Tool calls identified, setting state to waiting for tools")
                }
                Right(updatedState.withStatus(AgentStatus.WaitingForTools))
            }

          case Left(error) =>
            if (debug) {
              logger.error("[DEBUG] LLM completion failed: {}", error.message)
            }
            Left(error)
        }

      case AgentStatus.WaitingForTools =>
        // Get the latest assistant message with tool calls
        val assistantMessageOpt = state.conversation.messages.reverse
          .collectFirst { case msg: AssistantMessage if msg.toolCalls.nonEmpty => msg }

        assistantMessageOpt match {
          case Some(assistantMessage) =>
            // Log summary of tools to be processed
            val toolNames    = assistantMessage.toolCalls.map(_.name).mkString(", ")
            val logMessage   = s"[tools] executing ${assistantMessage.toolCalls.size} tools ($toolNames)"
            val stateWithLog = state.log(logMessage)

            if (debug) {
              logger.info("[DEBUG] Status: WaitingForTools -> processing tools")
              logger.info("[DEBUG] Processing {} tool calls: {}", assistantMessage.toolCalls.size, toolNames)
            }

            // Process the tool calls
            Try {
              if (debug) {
                logger.info("[DEBUG] Calling processToolCalls with {} tools", assistantMessage.toolCalls.size)
              } else {
                logger.debug("Processing {} tool calls", assistantMessage.toolCalls.size)
              }
              processToolCalls(stateWithLog, assistantMessage.toolCalls, debug)
            } match {
              case Success(newState) =>
                if (debug) {
                  logger.info("[DEBUG] Tool processing successful")
                  logger.info("[DEBUG] Status: WaitingForTools -> InProgress")
                } else {
                  logger.debug("Tool processing successful - continuing")
                }
                Right(newState.withStatus(AgentStatus.InProgress))
              case Failure(error) =>
                logger.error("Tool processing failed: {}", error.getMessage)
                if (debug) {
                  logger.error("[DEBUG] Status: WaitingForTools -> Failed")
                  logger.error("[DEBUG] Error: {}", error.getMessage)
                }
                Right(stateWithLog.withStatus(AgentStatus.Failed(error.getMessage)))
            }

          case None =>
            // Shouldn't happen, but handle gracefully
            if (debug) {
              logger.error("[DEBUG] No tool calls found in conversation - this should not happen!")
            }
            Right(state.withStatus(AgentStatus.Failed("No tool calls found in conversation")))
        }

      case _ =>
        // If the agent is already complete or failed, don't do anything
        if (debug) {
          logger.info("[DEBUG] Agent already in terminal state: {}", state.status)
        }
        Right(state)
    }

  /**
   * Process tool calls and add the results to the conversation
   */
  private def processToolCalls(state: AgentState, toolCalls: Seq[ToolCall], debug: Boolean): AgentState = {
    val toolRegistry = state.tools

    if (debug) {
      logger.info("[DEBUG] processToolCalls: Processing {} tool calls", toolCalls.size)
    }

    // Process each tool call and create tool messages
    val toolMessages = toolCalls.zipWithIndex.map { case (toolCall, index) =>
      val startTime = System.currentTimeMillis()

      if (debug) {
        logger.info("[DEBUG] Tool call {}/{}: {}", index + 1, toolCalls.size, toolCall.name)
        logger.info("[DEBUG]   Tool call ID: {}", toolCall.id)
        logger.info("[DEBUG]   Arguments (raw JSON): {}", toolCall.arguments)
        logger.info("[DEBUG]   Arguments type: {}", toolCall.arguments.getClass.getSimpleName)
      } else {
        logger.info("Executing tool: {} with arguments: {}", toolCall.name, toolCall.arguments)
      }

      val request = ToolCallRequest(toolCall.name, toolCall.arguments)

      if (debug) {
        logger.info("[DEBUG]   Created ToolCallRequest")
        logger.info("[DEBUG]   Executing via ToolRegistry...")
      }

      val result = toolRegistry.execute(request)

      val endTime  = System.currentTimeMillis()
      val duration = endTime - startTime

      val resultContent = result match {
        case Right(json) =>
          val jsonStr = json.render()
          if (debug) {
            logger.info("[DEBUG]   Tool {} SUCCESS in {}ms", toolCall.name, duration)
            logger.info("[DEBUG]   Result (raw JSON): {}", jsonStr)
            logger.info("[DEBUG]   Result type: {}", json.getClass.getSimpleName)
          } else {
            logger.info("Tool {} completed successfully in {}ms. Result: {}", toolCall.name, duration, jsonStr)
          }
          jsonStr
        case Left(error) =>
          val errorMessage = error.getFormattedMessage
          if (debug) {
            logger.error("[DEBUG]   Tool {} FAILED in {}ms", toolCall.name, duration)
            logger.error("[DEBUG]   Error type: {}", error.getClass.getSimpleName)
            logger.error("[DEBUG]   Error message: {}", errorMessage)
          }
          // Escape the error message for JSON
          val escapedMessage = errorMessage
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
          val errorJson = s"""{ "isError": true, "error": "$escapedMessage" }"""
          if (!debug) {
            logger.warn("Tool {} failed in {}ms with error: {}", toolCall.name, duration, errorMessage)
          }
          errorJson
      }

      if (debug) {
        logger.info("[DEBUG]   Creating ToolMessage with ID: {}", toolCall.id)
      }

      state.log(s"[tool] ${toolCall.name} (${duration}ms): $resultContent")
      ToolMessage(resultContent, toolCall.id)
    }

    if (debug) {
      logger.info("[DEBUG] All {} tool calls processed successfully", toolCalls.size)
      logger.info("[DEBUG] Adding {} tool messages to conversation", toolMessages.size)
    }

    // Add the tool messages to the conversation
    state.addMessages(toolMessages)
  }

  /**
   * Formats the agent state as a markdown document for tracing
   *
   * @param state The agent state to format as markdown
   * @return A markdown string representation of the agent state
   */
  def formatStateAsMarkdown(state: AgentState): String = {
    val sb = new StringBuilder()

    // Add header
    sb.append("# Agent Execution Trace\n\n")
    state.initialQuery.foreach(q => sb.append(s"**Initial Query:** $q\n"))
    sb.append(s"**Status:** ${state.status}\n")
    sb.append(s"**Tools Available:** ${state.tools.tools.map(_.name).mkString(", ")}\n\n")

    // Add conversation
    sb.append("## Conversation Flow\n\n")

    state.conversation.messages.zipWithIndex.foreach { case (message, index) =>
      val step = index + 1

      message.role match {
        case MessageRole.System =>
          sb.append(s"### Step $step: System Message\n\n")
          sb.append("```\n")
          sb.append(message.content)
          sb.append("\n```\n\n")

        case MessageRole.User =>
          sb.append(s"### Step $step: User Message\n\n")
          sb.append(message.content)
          sb.append("\n\n")

        case MessageRole.Assistant =>
          sb.append(s"### Step $step: Assistant Message\n\n")

          message match {
            case msg: AssistantMessage if msg.toolCalls.nonEmpty =>
              // Show content if it exists
              if (msg.content != null)
                if (msg.content.trim.nonEmpty) {
                  sb.append(msg.content)
                  sb.append("\n\n")
                } else
                  sb.append("--NO CONTENT--\n\n")

              sb.append("**Tool Calls:**\n\n")

              msg.toolCalls.foreach { tc =>
                sb.append(s"Tool: **${tc.name}**\n\n")
                sb.append("Arguments:\n")
                sb.append("```json\n")
                sb.append(tc.arguments)
                sb.append("\n```\n\n")
              }

            case _ =>
              sb.append(message.content)
              sb.append("\n\n")
          }

        case MessageRole.Tool =>
          message match {
            case msg: ToolMessage =>
              sb.append(s"### Step $step: Tool Response\n\n")
              sb.append(s"Tool Call ID: `${msg.toolCallId}`\n\n")
              sb.append("Result:\n")
              sb.append("```json\n")
              sb.append(msg.content)
              sb.append("\n```\n\n")

            case _ =>
              sb.append(s"### Step $step: Tool Response\n\n")
              sb.append("```\n")
              sb.append(message.content)
              sb.append("\n```\n\n")
          }

      }
    }

    // Add logs
    if (state.logs.nonEmpty) {
      sb.append("## Execution Logs\n\n")

      state.logs.zipWithIndex.foreach { case (log, index) =>
        sb.append(s"${index + 1}. ")

        // Format logs with code blocks for tool outputs
        log match {
          case l if l.startsWith("[assistant]") =>
            sb.append(s"**Assistant:** ${l.stripPrefix("[assistant] ")}\n")

          case l if l.startsWith("[tool]") =>
            val content = l.stripPrefix("[tool] ")
            sb.append(s"**Tool Output:** ${content}\n")

          case l if l.startsWith("[tools]") =>
            sb.append(s"**Tools:** ${l.stripPrefix("[tools] ")}\n")

          case l if l.startsWith("[system]") =>
            sb.append(s"**System:** ${l.stripPrefix("[system] ")}\n")

          case _ =>
            sb.append(s"$log\n")
        }
      }
    }

    sb.toString
  }

  /**
   * Writes the current state to a markdown trace file
   *
   * @param state The agent state to write to the trace log
   * @param traceLogPath The path to write the trace log to
   */
  def writeTraceLog(state: AgentState, traceLogPath: String): Unit = {
    import java.nio.charset.StandardCharsets
    import java.nio.file.{ Files, Paths }

    Safety
      .fromTry(Try {
        val content = formatStateAsMarkdown(state)
        Files.write(Paths.get(traceLogPath), content.getBytes(StandardCharsets.UTF_8))
      })
      .left
      .foreach(err => logger.error("Failed to write trace log: {}", err.message))
  }

  /**
   * Validate input using guardrails.
   *
   * If no guardrails are provided, input passes through unchanged.
   * If guardrails are provided, they are all evaluated and must all pass.
   *
   * @param query The input to validate
   * @param guardrails The guardrails to apply
   * @return Right(query) if valid, Left(error) if validation fails
   */
  private def validateInput(
    query: String,
    guardrails: Seq[InputGuardrail]
  ): Result[String] =
    if (guardrails.isEmpty) {
      Right(query)
    } else {
      // Run guardrails and aggregate results
      val composite = CompositeGuardrail.all(guardrails)
      composite.validate(query)
    }

  /**
   * Validate output using guardrails.
   *
   * If no guardrails are provided, output passes through unchanged.
   * If guardrails are provided, they are all evaluated and must all pass.
   *
   * @param state The agent state containing the output to validate
   * @param guardrails The guardrails to apply
   * @return Right(state) if valid, Left(error) if validation fails
   */
  private def validateOutput(
    state: AgentState,
    guardrails: Seq[OutputGuardrail]
  ): Result[AgentState] =
    if (guardrails.isEmpty) {
      Right(state)
    } else {
      // Extract final assistant message
      val finalMessage = state.conversation.messages
        .findLast(_.role == MessageRole.Assistant)
        .map(_.content)
        .getOrElse("")

      // Validate final message
      val composite = CompositeGuardrail.all(guardrails)
      composite.validate(finalMessage).map(_ => state)
    }

  /**
   * Runs the agent from an existing state until completion, failure, or step limit is reached
   *
   * @param initialState The initial agent state to run from
   * @param maxSteps Optional limit on the number of steps to execute
   * @param traceLogPath Optional path to write a markdown trace file
   * @param debug Enable detailed debug logging for tool calls and agent loop iterations
   * @return Either an error or the final agent state
   */
  def run(
    initialState: AgentState,
    maxSteps: Option[Int],
    traceLogPath: Option[String],
    debug: Boolean
  ): Result[AgentState] = {
    if (debug) {
      logger.info("[DEBUG] ========================================")
      logger.info("[DEBUG] Starting Agent.run")
      logger.info("[DEBUG] Max steps: {}", maxSteps.getOrElse("unlimited"))
      logger.info("[DEBUG] Trace log: {}", traceLogPath.getOrElse("disabled"))
      logger.info("[DEBUG] Initial status: {}", initialState.status)
      logger.info("[DEBUG] ========================================")
    }

    // Write initial state if tracing is enabled
    traceLogPath.foreach(path => writeTraceLog(initialState, path))

    @tailrec
    def runUntilCompletion(
      state: AgentState,
      stepsRemaining: Option[Int] = maxSteps,
      iteration: Int = 1
    ): Result[AgentState] =
      (state.status, stepsRemaining) match {
        // Check for step limit before executing either type of step
        case (s, Some(0)) if s == AgentStatus.InProgress || s == AgentStatus.WaitingForTools =>
          if (debug) {
            logger.warn("[DEBUG] ========================================")
            logger.warn("[DEBUG] ITERATION {}: Step limit reached!", iteration)
            logger.warn("[DEBUG] ========================================")
          }
          // Step limit reached
          val updatedState =
            state.log("[system] Step limit reached").withStatus(AgentStatus.Failed("Maximum step limit reached"))

          // Write final state if tracing is enabled
          traceLogPath.foreach(path => writeTraceLog(updatedState, path))
          Right(updatedState)

        // Continue if we're in progress or waiting for tools
        case (s, _) if s == AgentStatus.InProgress || s == AgentStatus.WaitingForTools =>
          if (debug) {
            logger.info("[DEBUG] ========================================")
            logger.info("[DEBUG] ITERATION {}", iteration)
            logger.info("[DEBUG] Current status: {}", state.status)
            logger.info("[DEBUG] Steps remaining: {}", stepsRemaining.map(_.toString).getOrElse("unlimited"))
            logger.info("[DEBUG] Conversation messages: {}", state.conversation.messages.size)
            logger.info("[DEBUG] ========================================")
          }

          runStep(state, debug) match {
            case Right(newState) =>
              // Only decrement steps when going from InProgress to WaitingForTools or back to InProgress
              // This means one "logical step" includes both the LLM call and tool execution
              val shouldDecrementStep =
                (state.status == AgentStatus.InProgress && newState.status == AgentStatus.WaitingForTools) ||
                  (state.status == AgentStatus.WaitingForTools && newState.status == AgentStatus.InProgress)

              val nextSteps = if (shouldDecrementStep) stepsRemaining.map(_ - 1) else stepsRemaining

              if (debug && shouldDecrementStep) {
                logger.info(
                  "[DEBUG] Step completed. Next steps remaining: {}",
                  nextSteps.map(_.toString).getOrElse("unlimited")
                )
              }

              // Write updated state if tracing is enabled
              traceLogPath.foreach(path => writeTraceLog(newState, path))

              runUntilCompletion(newState, nextSteps, iteration + 1)

            case Left(error) =>
              if (debug) {
                logger.error("[DEBUG] ========================================")
                logger.error("[DEBUG] ITERATION {}: Agent failed with error", iteration)
                logger.error("[DEBUG] Error: {}", error.message)
                logger.error("[DEBUG] ========================================")
              }
              Left(error)
          }

        case (_, _) =>
          if (debug) {
            logger.info("[DEBUG] ========================================")
            logger.info("[DEBUG] Agent completed")
            logger.info("[DEBUG] Final status: {}", state.status)
            logger.info("[DEBUG] Total iterations: {}", iteration)
            logger.info("[DEBUG] ========================================")
          }
          // Write final state if tracing is enabled
          traceLogPath.foreach(path => writeTraceLog(state, path))
          Right(state) // Complete or Failed
      }

    runUntilCompletion(initialState)
  }

  /**
   * Runs the agent with a new query until completion, failure, or step limit is reached
   *
   * @param query The user query to process
   * @param tools The registry of available tools
   * @param inputGuardrails Validate query before processing (default: none)
   * @param outputGuardrails Validate response before returning (default: none)
   * @param maxSteps Optional limit on the number of steps to execute
   * @param traceLogPath Optional path to write a markdown trace file
   * @param systemPromptAddition Optional additional text to append to the default system prompt
   * @param completionOptions Optional completion options for LLM calls (temperature, maxTokens, etc.)
   * @param debug Enable detailed debug logging for tool calls and agent loop iterations
   * @return Either an error or the final agent state
   */
  def run(
    query: String,
    tools: ToolRegistry,
    inputGuardrails: Seq[InputGuardrail] = Seq.empty,
    outputGuardrails: Seq[OutputGuardrail] = Seq.empty,
    maxSteps: Option[Int] = None,
    traceLogPath: Option[String] = None,
    systemPromptAddition: Option[String] = None,
    completionOptions: CompletionOptions = CompletionOptions(),
    debug: Boolean = false
  ): Result[AgentState] =
    for {
      // 1. Validate input
      validatedQuery <- validateInput(query, inputGuardrails)

      // 2. Initialize and run agent
      _ = if (debug) {
        logger.info("[DEBUG] ========================================")
        logger.info("[DEBUG] Initializing new agent with query")
        logger.info("[DEBUG] Query: {}", validatedQuery)
        logger.info("[DEBUG] Tools: {}", tools.tools.map(_.name).mkString(", "))
        logger.info("[DEBUG] Input guardrails: {}", inputGuardrails.map(_.name).mkString(", "))
        logger.info("[DEBUG] Output guardrails: {}", outputGuardrails.map(_.name).mkString(", "))
        logger.info("[DEBUG] ========================================")
      }
      initialState = initialize(validatedQuery, tools, systemPromptAddition, completionOptions)
      finalState <- run(initialState, maxSteps, traceLogPath, debug)

      // 3. Validate output
      validatedState <- validateOutput(finalState, outputGuardrails)
    } yield validatedState

  /**
   * Continue an agent conversation with a new user message.
   * This is the functional way to handle multi-turn conversations.
   *
   * The previous state must be in Complete or Failed status - cannot continue from InProgress or WaitingForTools.
   * This ensures a clean turn boundary and prevents inconsistent state.
   *
   * @param previousState The previous agent state (must be Complete or Failed)
   * @param newUserMessage The new user message to process
   * @param inputGuardrails Validate new message before processing
   * @param outputGuardrails Validate response before returning
   * @param maxSteps Optional limit on reasoning steps for this turn
   * @param traceLogPath Optional path for trace logging
   * @param contextWindowConfig Optional configuration for automatic context pruning
   * @param debug Enable debug logging
   * @return Result containing the new agent state after processing the message
   *
   * @example
   * {{{
   * val result = for {
   *   client <- LLMConnect.fromEnv()
   *   tools = new ToolRegistry(Seq(WeatherTool.tool))
   *   agent = new Agent(client)
   *   state1 <- agent.run("What's the weather in Paris?", tools)
   *   state2 <- agent.continueConversation(state1, "And in London?")
   *   state3 <- agent.continueConversation(state2, "Which is warmer?")
   * } yield state3
   * }}}
   */
  def continueConversation(
    previousState: AgentState,
    newUserMessage: String,
    inputGuardrails: Seq[InputGuardrail] = Seq.empty,
    outputGuardrails: Seq[OutputGuardrail] = Seq.empty,
    maxSteps: Option[Int] = None,
    traceLogPath: Option[String] = None,
    contextWindowConfig: Option[ContextWindowConfig] = None,
    debug: Boolean = false
  ): Result[AgentState] = {
    import org.llm4s.error.ValidationError

    for {
      // 1. Validate input
      validatedMessage <- validateInput(newUserMessage, inputGuardrails)

      // 2. Validate previous state and continue
      finalState <- previousState.status match {
        case AgentStatus.Complete | AgentStatus.Failed(_) =>
          // Prepare new state by adding user message and resetting status
          val stateWithNewMessage = previousState.copy(
            conversation = previousState.conversation.addMessage(UserMessage(validatedMessage)),
            status = AgentStatus.InProgress,
            logs = Seq.empty // Reset logs for new turn
          )

          // Optionally prune before running
          val stateToRun = contextWindowConfig match {
            case Some(config) =>
              AgentState.pruneConversation(stateWithNewMessage, config)
            case None =>
              stateWithNewMessage
          }

          // Run from the new state
          run(stateToRun, maxSteps, traceLogPath, debug)

        case AgentStatus.InProgress | AgentStatus.WaitingForTools =>
          Left(
            ValidationError.invalid(
              "agentState",
              "Cannot continue from an incomplete conversation. " +
                "Previous state must be Complete or Failed. " +
                s"Current status: ${previousState.status}"
            )
          )
      }

      // 3. Validate output
      validatedState <- validateOutput(finalState, outputGuardrails)
    } yield validatedState
  }

  /**
   * Run multiple conversation turns sequentially.
   * Each turn waits for the previous to complete before starting.
   * This is a convenience method for running a complete multi-turn conversation.
   *
   * @param initialQuery The first user message
   * @param followUpQueries Additional user messages to process in sequence
   * @param tools Tool registry for the conversation
   * @param maxStepsPerTurn Optional step limit per turn
   * @param systemPromptAddition Optional system prompt addition
   * @param completionOptions Completion options
   * @param contextWindowConfig Optional configuration for automatic context pruning
   * @param debug Enable debug logging
   * @return Result containing the final agent state after all turns
   *
   * @example
   * {{{
   * val result = agent.runMultiTurn(
   *   initialQuery = "What's the weather in Paris?",
   *   followUpQueries = Seq(
   *     "And in London?",
   *     "Which is warmer?"
   *   ),
   *   tools = tools
   * )
   * }}}
   */
  def runMultiTurn(
    initialQuery: String,
    followUpQueries: Seq[String],
    tools: ToolRegistry,
    maxStepsPerTurn: Option[Int] = None,
    systemPromptAddition: Option[String] = None,
    completionOptions: CompletionOptions = CompletionOptions(),
    contextWindowConfig: Option[ContextWindowConfig] = None,
    debug: Boolean = false
  ): Result[AgentState] = {
    // Run first turn
    val firstTurn = run(
      query = initialQuery,
      tools = tools,
      inputGuardrails = Seq.empty,
      outputGuardrails = Seq.empty,
      maxSteps = maxStepsPerTurn,
      traceLogPath = None,
      systemPromptAddition = systemPromptAddition,
      completionOptions = completionOptions,
      debug = debug
    )

    // Fold over follow-up queries, threading state through
    followUpQueries.foldLeft(firstTurn) { (stateResult, query) =>
      stateResult.flatMap { state =>
        continueConversation(
          previousState = state,
          newUserMessage = query,
          inputGuardrails = Seq.empty,
          outputGuardrails = Seq.empty,
          maxSteps = maxStepsPerTurn,
          traceLogPath = None,
          contextWindowConfig = contextWindowConfig,
          debug = debug
        )
      }
    }
  }
}
