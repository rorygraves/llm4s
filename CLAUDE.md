# CLAUDE.md - AI Assistant Guide for LLM4S

> **Last Updated:** 2025-11-15
> **Purpose:** Comprehensive guide for AI assistants working with the LLM4S codebase

## Table of Contents

1. [Project Overview](#project-overview)
2. [Repository Structure](#repository-structure)
3. [Architecture & Key Concepts](#architecture--key-concepts)
4. [Development Workflows](#development-workflows)
5. [Code Conventions & Style](#code-conventions--style)
6. [Configuration Management](#configuration-management)
7. [Testing & Quality Assurance](#testing--quality-assurance)
8. [Common Development Tasks](#common-development-tasks)
9. [AI Assistant Best Practices](#ai-assistant-best-practices)

---

## Project Overview

### What is LLM4S?

LLM4S (Large Language Models for Scala) is a comprehensive framework for building LLM-powered applications in Scala. It provides:

- **Multi-Provider Support**: Unified API for OpenAI, Anthropic, Azure OpenAI, and Ollama
- **Type-Safe Design**: Leveraging Scala's type system for compile-time safety
- **Functional Programming**: Built on Cats and functional programming principles
- **Multimodal Capabilities**: Support for text, images, voice, and other modalities
- **Agent Framework**: Tools for building single and multi-agent workflows
- **RAG Support**: Built-in retrieval-augmented generation capabilities
- **Observability**: Comprehensive tracing with Langfuse integration
- **Secure Execution**: Containerized workspace for safe tool execution

### Technology Stack

- **Languages**: Scala 2.13.16 and Scala 3.7.1 (cross-compiled)
- **Build Tool**: SBT with cross-compilation support
- **Testing**: ScalaTest + Scalamock
- **Core Libraries**: Cats, Monocle, uPickle
- **LLM SDKs**: Azure OpenAI Java SDK, Anthropic Java SDK
- **Containerization**: Docker for workspace isolation
- **CI/CD**: GitHub Actions

### Design Philosophy

1. **Type Safety First**: Use Scala's type system to catch errors at compile time
2. **Functional Core**: Immutable data structures and pure functions where possible
3. **Result-Based Error Handling**: Use `Result[A]` (Either[LLMError, A]) instead of exceptions
4. **Configuration via Types**: Typed configuration loaders instead of raw env vars
5. **Multi-Version Support**: Maintain compatibility with both Scala 2.13 and 3.x
6. **Observability Built-In**: Tracing as a first-class concern

---

## Repository Structure

### Top-Level Layout

```
llm4s/
├── .github/              # GitHub Actions workflows and templates
│   └── workflows/        # CI, release, and code review workflows
├── docs/                 # Documentation and design documents
├── hooks/                # Git pre-commit hooks
├── modules/              # All code modules
│   ├── core/            # Core library (published)
│   ├── samples/         # Usage examples (not published)
│   ├── workspace/       # Containerized execution environment
│   │   ├── workspaceShared/   # Shared workspace types
│   │   ├── workspaceRunner/   # Docker container service
│   │   ├── workspaceClient/   # Client for workspace
│   │   └── workspaceSamples/  # Workspace examples
│   └── crossTest/       # Cross-version compatibility tests
│       ├── scala2/      # Scala 2.13 compatibility tests
│       └── scala3/      # Scala 3 compatibility tests
├── project/              # SBT build configuration
├── szork/                # Szork game implementation (demo)
├── build.sbt             # Main build definition
├── .scalafmt.conf        # Code formatting rules
└── .scalafix.conf        # Code linting rules
```

### Core Module Structure (modules/core/src/main/scala/org/llm4s/)

```
org/llm4s/
├── agent/                # Agent framework
│   └── orchestration/   # Multi-agent orchestration
├── assistant/            # Interactive assistant implementation
├── config/               # Configuration loading (ConfigReader)
├── context/              # Conversation context management
│   └── tokens/          # Token counting and window management
├── core/                 # Core utilities
│   └── safety/          # Resource safety (Using, Try conversions)
├── error/                # Error types (LLMError hierarchy)
├── imagegeneration/      # Image generation (DALL-E, Stable Diffusion, etc.)
├── imageprocessing/      # Image analysis (Claude Vision, etc.)
├── llmconnect/           # Multi-provider LLM client
│   ├── config/          # Provider configurations
│   ├── model/           # Common data models (messages, completions)
│   ├── provider/        # Provider implementations (OpenAI, Anthropic, etc.)
│   ├── streaming/       # Streaming response handling
│   └── utils/           # Client utilities
├── mcp/                  # Model Context Protocol integration
├── resource/             # RAG and document processing
├── speech/               # Speech-to-text and text-to-speech
│   ├── stt/             # Speech-to-text (Vosk, etc.)
│   └── tts/             # Text-to-speech
├── toolapi/              # Tool calling framework
├── trace/                # Observability and tracing
└── types/                # Core type definitions (Result, newtypes)
```

### Key Files to Know

| File/Directory | Purpose |
|---------------|---------|
| `modules/core/src/main/scala/org/llm4s/types/package.scala` | Core type definitions (Result, ModelName, etc.) |
| `modules/core/src/main/scala/org/llm4s/config/ConfigReader.scala` | Central configuration loader |
| `modules/core/src/main/scala/org/llm4s/llmconnect/LLMClient.scala` | Main LLM client interface |
| `modules/core/src/main/scala/org/llm4s/toolapi/ToolFunction.scala` | Tool calling framework |
| `modules/core/src/main/scala/org/llm4s/trace/` | Tracing implementations |
| `modules/samples/src/main/scala/org/llm4s/samples/` | Working examples |
| `project/Dependencies.scala` | Dependency versions |
| `build.sbt` | Project structure and settings |

---

## Architecture & Key Concepts

### Core Type System

#### Result Type

The foundation of error handling in LLM4S:

```scala
// Defined in org.llm4s.types
type Result[+A] = Either[error.LLMError, A]

// Usage
def parseModel(name: String): Result[ModelName] = {
  if (name.isEmpty) Left(ValidationError("Model name cannot be empty"))
  else Right(ModelName(name))
}
```

#### Type-Safe Newtypes

LLM4S uses `AnyVal` wrapper types for type safety without runtime overhead:

```scala
final case class ModelName(value: String) extends AnyVal
final case class ApiKey(private val value: String) extends AnyVal {
  override def toString: String = "ApiKey(***)"  // Prevents logging
}
final case class ConversationId(value: String) extends AnyVal
```

#### Async and Validated Results

```scala
type AsyncResult[+A] = Future[Result[A]]
type ValidatedResult[+A] = ValidatedNec[error.LLMError, A]
type StreamResult[+A] = Result[Iterator[A]]
```

### Error Handling

**DO NOT use exceptions**. Use `Result[A]` instead:

```scala
// ❌ BAD - Don't do this
def parseConfig(): Config = {
  throw new RuntimeException("Config missing")
}

// ✅ GOOD - Return Result
def parseConfig(): Result[Config] = {
  ConfigReader.LLMConfig()
    .toResult
    .left.map(err => ConfigurationError(s"Failed to load config: $err"))
}
```

**Try to Result conversion**:

```scala
import org.llm4s.types.TryOps  // Provides .toResult

val result: Result[Int] = Try {
  "123".toInt
}.toResult  // Converts to Result[Int]
```

### Configuration Pattern

**ALWAYS use ConfigReader** - never access `sys.env` or `System.getenv` directly:

```scala
import org.llm4s.config.ConfigReader

// ❌ BAD
val apiKey = sys.env.get("OPENAI_API_KEY")

// ✅ GOOD
val providerConfig: Result[ProviderConfig] = ConfigReader.Provider()
val tracingSettings: Result[TracingSettings] = ConfigReader.TracingConf()
```

### Multi-Provider Architecture

LLM4S provides a unified interface across providers:

```scala
// Auto-selects provider based on LLM_MODEL env var
val client: Result[LLMClient] = LLMConnect.create()

// All providers use the same interface
client.flatMap { c =>
  c.complete(
    messages = List(UserMessage("Hello")),
    model = None  // Uses configured model
  )
}
```

Supported providers:
- **OpenAI**: `openai/gpt-4o`, `openai/gpt-4-turbo`, etc.
- **Anthropic**: `anthropic/claude-3-7-sonnet-latest`, etc.
- **Azure OpenAI**: `azure/gpt-4o`
- **Ollama**: `ollama/llama2`, `ollama/mistral`

### Cross-Version Compatibility

The codebase supports both Scala 2.13 and 3.x:

```
src/main/scala/       # Common code for both versions
src/main/scala-2.13/  # Scala 2.13-specific code
src/main/scala-3/     # Scala 3-specific code
```

When writing code:
1. **Prefer common code** in `src/main/scala/`
2. **Use version-specific code** only when necessary (macros, syntax differences)
3. **Test both versions** with `sbt +test`

---

## Development Workflows

### Initial Setup

```bash
# Prerequisites: JDK 21+, SBT, Docker (optional)

# Clone and setup
git clone https://github.com/llm4s/llm4s.git
cd llm4s

# Install pre-commit hooks (recommended)
./hooks/install.sh

# Build all versions
sbt buildAll
```

### Environment Configuration

Create `.env` file (gitignored) or export:

```bash
# For OpenAI
export LLM_MODEL=openai/gpt-4o
export OPENAI_API_KEY=sk-...

# For Anthropic
export LLM_MODEL=anthropic/claude-3-7-sonnet-latest
export ANTHROPIC_API_KEY=sk-ant-...

# For tracing (optional)
export TRACING_MODE=langfuse  # or "console" or "none"
export LANGFUSE_PUBLIC_KEY=pk-lf-...
export LANGFUSE_SECRET_KEY=sk-lf-...
```

### Common SBT Commands

```bash
# Compilation
sbt compile              # Current Scala version
sbt +compile             # All Scala versions
sbt compileAll           # Alias for +compile

# Testing
sbt test                 # Current version
sbt +test                # All versions
sbt testAll              # All + cross tests
sbt testCross            # Cross-version artifact tests

# Formatting
sbt scalafmtAll          # Format all code
sbt scalafmtCheckAll     # Check formatting (CI)

# Coverage
sbt cov                  # Run tests with coverage
sbt covReport            # Coverage report only

# Running samples
sbt "samples/runMain org.llm4s.samples.basic.BasicLLMCallingExample"

# Docker (workspace)
sbt docker:publishLocal  # Build workspace image
```

### Git Workflow

1. **Branch naming**: Use descriptive names (`feature/add-tool-calling`, `fix/streaming-bug`)
2. **Commits**: Imperative mood, clear messages
3. **Pre-commit**: Runs automatically if hooks installed
4. **Pull requests**: Include tests, update docs/samples

### Pre-Commit Checks

The pre-commit hook runs:
1. `sbt scalafmtCheckAll` - Format verification
2. `sbt +compile` - Compilation for all Scala versions
3. `sbt +test` - Tests for all Scala versions

Skip with `git commit --no-verify` (not recommended).

---

## Code Conventions & Style

### Scala Style Guide

#### Formatting (scalafmt)

- **Indentation**: 2 spaces
- **Max line length**: 120 characters
- **Alignment**: Aggressive alignment enabled
- **Trailing commas**: Preserved
- **Import ordering**: Automatic

Run `sbt scalafmtAll` before committing.

#### Naming Conventions

```scala
// Types: PascalCase
trait LLMClient
case class CompletionResponse()
type Result[A] = Either[LLMError, A]

// Values/functions: camelCase
val apiKey: ApiKey = ???
def createClient(): Result[LLMClient] = ???

// Constants: camelCase (not SCREAMING_CASE)
val defaultTimeout: Duration = 30.seconds
val maxRetries: Int = 3

// Type parameters: Single uppercase letter
def map[A, B](f: A => B): Result[B]
```

#### Package Structure

```scala
// Match package to directory
// File: modules/core/src/main/scala/org/llm4s/config/ConfigReader.scala
package org.llm4s.config

object ConfigReader {
  // ...
}
```

### Scalafix Rules

The project enforces rules via `.scalafix.conf`:

#### ❌ BANNED Patterns

1. **Raw Config Access**
   ```scala
   // ❌ BAD
   ConfigFactory.load()
   sys.env("API_KEY")
   System.getenv("API_KEY")

   // ✅ GOOD
   ConfigReader.Provider()
   ```

2. **Exception Handling** (outside allowed packages)
   ```scala
   // ❌ BAD
   try {
     riskyOperation()
   } catch {
     case e: Exception => ???
   } finally {
     cleanup()
   }

   // ✅ GOOD
   Try(riskyOperation()).toResult
   // or
   Safety.using(resource) { r => ??? }
   ```

3. **Infix Operators** (prefer explicit)
   ```scala
   // ❌ BAD
   list map func

   // ✅ GOOD
   list.map(func)
   ```

#### Allowed Exceptions

These packages can use try/catch/finally:
- `org.llm4s.core.safety` - Resource management
- `org.llm4s.agent.orchestration` - MDC, cancellation
- `org.llm4s.config` - ConfigFactory only here

### Documentation

```scala
/**
 * Comprehensive docstring for public API.
 *
 * @param messages The conversation history
 * @param model Optional model override
 * @return Completion response or error
 */
def complete(
  messages: List[Message],
  model: Option[ModelName] = None
): Result[CompletionResponse]
```

### Import Organization

```scala
// 1. Java/Scala standard library
import scala.concurrent.Future
import java.time.Instant

// 2. Third-party libraries
import cats.data.NonEmptyList
import upickle.default._

// 3. Project packages
import org.llm4s.types._
import org.llm4s.config.ConfigReader
```

---

## Configuration Management

### Configuration Hierarchy

LLM4S uses HOCON (via Typesafe Config) with precedence:

1. **System properties** (`-D` flags): Highest priority
2. **Environment variables** (via `${?ENV}` in reference.conf)
3. **`application.conf`** (user-provided, optional)
4. **`reference.conf`** (library defaults)

### ConfigReader - The Central API

```scala
import org.llm4s.config.ConfigReader

// Load LLM configuration
val llmConfig: Result[LLMConfig] = ConfigReader.LLMConfig()

// Load provider-specific config
val provider: Result[ProviderConfig] = ConfigReader.Provider()
// Returns: OpenAIConfig | AnthropicConfig | AzureConfig | OllamaConfig

// Load tracing configuration
val tracing: Result[TracingSettings] = ConfigReader.TracingConf()

// Load embeddings config (samples only)
val embeddingSettings: Result[EmbeddingUiSettings] =
  EmbeddingUiSettings.load()
```

### Environment Variables

#### Core LLM Settings

| Variable | Example | Description |
|----------|---------|-------------|
| `LLM_MODEL` | `openai/gpt-4o` | Model identifier (required) |
| `OPENAI_API_KEY` | `sk-...` | OpenAI API key |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Override base URL |
| `ANTHROPIC_API_KEY` | `sk-ant-...` | Anthropic API key |
| `ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | Override base URL |
| `AZURE_API_KEY` | `...` | Azure OpenAI key |
| `AZURE_API_BASE` | `https://...` | Azure endpoint |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server |

#### Tracing Settings

| Variable | Example | Description |
|----------|---------|-------------|
| `TRACING_MODE` | `langfuse` | `langfuse`, `console`, or `none` |
| `LANGFUSE_PUBLIC_KEY` | `pk-lf-...` | Langfuse public key |
| `LANGFUSE_SECRET_KEY` | `sk-lf-...` | Langfuse secret key |
| `LANGFUSE_URL` | `https://cloud.langfuse.com` | Langfuse server |

#### Workspace Settings (samples)

| Variable | Example | Description |
|----------|---------|-------------|
| `WORKSPACE_DIR` | `/tmp/workspace` | Working directory |
| `WORKSPACE_IMAGE` | `llm4s/workspace-runner` | Docker image |
| `WORKSPACE_PORT` | `8080` | Container port |

### Typed Configuration Pattern

```scala
// Instead of raw strings
val model: String = sys.env("LLM_MODEL")  // ❌

// Use typed loaders
val provider: Result[ProviderConfig] = ConfigReader.Provider()
provider.map { config =>
  val modelName: ModelName = config.model
  val apiKey: ApiKey = config.apiKey
}  // ✅
```

---

## Testing & Quality Assurance

### Test Organization

```
modules/core/
├── src/main/scala/org/llm4s/foo/Bar.scala
└── src/test/scala/org/llm4s/foo/BarSpec.scala  # or BarTest.scala
```

### Testing Frameworks

- **ScalaTest**: Primary testing framework
- **Scalamock**: Mocking (when needed)

```scala
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MyComponentSpec extends AnyFlatSpec with Matchers {
  "MyComponent" should "return success for valid input" in {
    val result = MyComponent.process("valid")
    result shouldBe Right(ProcessedData(...))
  }

  it should "return error for invalid input" in {
    val result = MyComponent.process("")
    result.isLeft shouldBe true
  }
}
```

### Testing Best Practices

1. **Deterministic**: Tests must produce same results every run
2. **Fast**: Avoid network I/O, use mocks/stubs
3. **Isolated**: Each test is independent
4. **Coverage**: Target ≥80% statement coverage

```bash
# Run coverage
sbt cov

# View HTML report
open target/scala-3.7.1/scoverage-report/index.html
```

### Cross-Version Testing

```bash
# Quick test (both versions)
sbt +test

# Cross-compilation test (against published artifacts)
sbt testCross

# Full clean + publish + cross test
sbt fullCrossTest
```

### Mocking Example

```scala
import org.scalamock.scalatest.MockFactory

class LLMClientSpec extends AnyFlatSpec with MockFactory {
  "LLMClient" should "retry on failure" in {
    val mockClient = mock[LLMClient]

    (mockClient.complete _)
      .expects(*, *)
      .returning(Left(NetworkError("timeout")))
      .once()

    (mockClient.complete _)
      .expects(*, *)
      .returning(Right(CompletionResponse(...)))
      .once()

    val result = RetryClient(mockClient).completeWithRetry(...)
    result.isRight shouldBe true
  }
}
```

---

## Common Development Tasks

### Adding a New Sample

1. **Create file** in `modules/samples/src/main/scala/org/llm4s/samples/<category>/`
2. **Implement** with `main` method
3. **Document** with clear comments
4. **Run** with `sbt "samples/runMain org.llm4s.samples.<category>.YourExample"`

```scala
package org.llm4s.samples.basic

import org.llm4s.llmconnect.LLMConnect
import org.llm4s.llmconnect.model.UserMessage

object MyNewExample extends App {
  val result = for {
    client <- LLMConnect.create()
    response <- client.complete(
      messages = List(UserMessage("Hello, world!")),
      model = None
    )
  } yield response

  result match {
    case Right(completion) =>
      println(s"Response: ${completion.content}")
    case Left(error) =>
      println(s"Error: $error")
  }
}
```

### Adding a New Tool

1. **Define** tool function with ScalaDoc
2. **Register** with tool registry
3. **Test** tool execution
4. **Document** in samples

```scala
/**
 * Fetches current weather for a location.
 *
 * @param location City name or coordinates
 * @param unit Temperature unit (celsius/fahrenheit)
 * @return Weather description
 */
def getWeather(location: String, unit: String = "celsius"): Result[String] = {
  // Implementation
  Right(s"Sunny, 72°F in $location")
}

// Tool registration happens automatically via ToolFunction.apply
```

### Working with Multi-Turn Conversations

**Multi-turn conversation support** was added in Phase 1.1 to provide a functional, immutable API for agent conversations.

#### Basic Multi-Turn Pattern

```scala
import org.llm4s.agent.Agent
import org.llm4s.llmconnect.LLMConnect
import org.llm4s.toolapi.ToolRegistry

val result = for {
  client <- LLMConnect.fromEnv()
  tools = new ToolRegistry(Seq(WeatherTool.tool))
  agent = new Agent(client)

  // Turn 1
  state1 <- agent.run("What's the weather in Paris?", tools)

  // Turn 2 - continue from state1
  state2 <- agent.continueConversation(state1, "And in London?")

  // Turn 3 - continue from state2
  state3 <- agent.continueConversation(state2, "Which is warmer?")

} yield state3
```

**No `var`, no mutation, pure functional style!**

#### Multi-Turn Helper Method

For convenience, use `runMultiTurn` to chain turns automatically:

```scala
val result = agent.runMultiTurn(
  initialQuery = "What's the weather in Paris?",
  followUpQueries = Seq(
    "And in London?",
    "Which is warmer?"
  ),
  tools = tools
)
```

#### Context Window Management

For long conversations, use `ContextWindowConfig` to automatically prune history:

```scala
import org.llm4s.agent.{ ContextWindowConfig, PruningStrategy }

val contextConfig = ContextWindowConfig(
  maxMessages = Some(20),         // Keep max 20 messages
  preserveSystemMessage = true,   // Always keep system message
  pruningStrategy = PruningStrategy.OldestFirst  // Drop oldest first
)

val state2 = agent.continueConversation(
  state1,
  "Next question?",
  contextWindowConfig = Some(contextConfig)
)
```

**Available Pruning Strategies:**
- `PruningStrategy.OldestFirst` - Remove oldest messages (FIFO)
- `PruningStrategy.MiddleOut` - Keep start and end, remove middle
- `PruningStrategy.RecentTurnsOnly(n)` - Keep only last N turns
- `PruningStrategy.Custom(fn)` - Custom function

#### Conversation Persistence

Save and load agent state for resumable conversations:

```scala
import org.llm4s.agent.AgentState

// Save state to file
AgentState.saveToFile(state, "/tmp/conversation.json")

// Later, in a different session...
for {
  tools = new ToolRegistry(Seq(...))  // Must recreate tools
  loadedState <- AgentState.loadFromFile("/tmp/conversation.json", tools)
  state2 <- agent.continueConversation(loadedState, "Continue...")
} yield state2
```

**Note:** Tools are not serialized (they contain function references) and must be re-registered when loading state.

#### Examples

- **Multi-turn**: `samples/runMain org.llm4s.samples.agent.MultiTurnConversationExample`
- **Context pruning**: `samples/runMain org.llm4s.samples.agent.LongConversationExample`
- **Persistence**: `samples/runMain org.llm4s.samples.agent.ConversationPersistenceExample`

### Adding a New Provider

1. **Create** provider config in `llmconnect/config/`
2. **Implement** client in `llmconnect/provider/`
3. **Update** `ProviderSelector` to recognize provider
4. **Add** tests for provider-specific behavior
5. **Document** in README and samples

### Updating Dependencies

1. **Edit** `project/Dependencies.scala`
2. **Update** version number
3. **Test** with `sbt buildAll`
4. **Check** for binary compatibility issues

```scala
// project/Dependencies.scala
object Versions {
  val cats = "2.13.0"  // Update here
}
```

### Creating a Migration Guide

When making breaking changes:

1. **Document** in `docs/MIGRATION_GUIDE.md`
2. **Provide** before/after examples
3. **Explain** rationale
4. **Update** samples

---

## AI Assistant Best Practices

### When Writing Code

1. **Always prefer Result over exceptions**
   ```scala
   // ✅ Return Result
   def operation(): Result[Data] = ???

   // ❌ Don't throw
   def operation(): Data = throw new Exception()
   ```

2. **Use ConfigReader for all configuration**
   ```scala
   // ✅
   val config = ConfigReader.Provider()

   // ❌
   val key = sys.env("API_KEY")
   ```

3. **Cross-version awareness**
   - Check if code works in both Scala 2.13 and 3.x
   - Use version-specific directories when needed
   - Test with `sbt +compile`

4. **Type-safe newtypes**
   ```scala
   // ✅ Type-safe
   def setModel(model: ModelName): Unit = ???

   // ❌ Stringly-typed
   def setModel(model: String): Unit = ???
   ```

5. **Comprehensive error handling**
   ```scala
   for {
     client <- LLMConnect.create()
     response <- client.complete(messages, None)
     parsed <- parseResponse(response)
   } yield parsed
   ```

### When Answering Questions

1. **Check the source**: Read actual code, don't assume
2. **Provide file paths**: Help users locate code (`modules/core/src/main/scala/...`)
3. **Show examples**: Link to relevant samples
4. **Explain context**: Why does the code work this way?

### When Making Changes

1. **Run tests**: `sbt +test` at minimum
2. **Check formatting**: `sbt scalafmtCheckAll`
3. **Update docs**: If behavior changes, update README or docs
4. **Add samples**: Show how to use new features

### Understanding the Codebase

**Key architectural files to read first:**

1. `modules/core/src/main/scala/org/llm4s/types/package.scala` - Type system
2. `modules/core/src/main/scala/org/llm4s/config/ConfigReader.scala` - Configuration
3. `modules/core/src/main/scala/org/llm4s/llmconnect/LLMClient.scala` - Client interface
4. `modules/samples/src/main/scala/org/llm4s/samples/basic/BasicLLMCallingExample.scala` - Basic usage

**Common patterns to recognize:**

- **Result chaining**: `for { a <- op1(); b <- op2(a) } yield b`
- **Config loading**: `ConfigReader.X().flatMap { config => ... }`
- **Provider abstraction**: Single interface, multiple implementations
- **Type-safe wrappers**: `ModelName(...)`, `ApiKey(...)`, etc.

### Useful Search Queries

```bash
# Find all LLM client implementations
find modules/core -name "*Client.scala" -path "*/llmconnect/*"

# Find samples for a feature
find modules/samples -name "*Agent*.scala"

# Find tests for a module
find modules/core -path "*/test/scala/*" -name "*Spec.scala"

# Check cross-version code
ls modules/core/src/main/scala-{2.13,3}/
```

---

## Quick Reference

### File Navigation

| You need to... | Look here |
|----------------|-----------|
| Understand core types | `modules/core/src/main/scala/org/llm4s/types/package.scala` |
| See how to configure LLM | `modules/core/src/main/scala/org/llm4s/config/ConfigReader.scala` |
| Find usage examples | `modules/samples/src/main/scala/org/llm4s/samples/` |
| Check provider implementations | `modules/core/src/main/scala/org/llm4s/llmconnect/provider/` |
| Learn about tools | `modules/core/src/main/scala/org/llm4s/toolapi/` |
| Understand agents | `modules/core/src/main/scala/org/llm4s/agent/` |
| See tracing | `modules/core/src/main/scala/org/llm4s/trace/` |
| Understand workspace | `modules/workspace/` |

### Common Commands

| Task | Command |
|------|---------|
| Build everything | `sbt buildAll` |
| Run tests | `sbt +test` |
| Format code | `sbt scalafmtAll` |
| Check formatting | `sbt scalafmtCheckAll` |
| Run example | `sbt "samples/runMain org.llm4s.samples.basic.BasicLLMCallingExample"` |
| Coverage report | `sbt cov` |
| Cross-version test | `sbt testCross` |
| Build Docker image | `sbt docker:publishLocal` |

### Environment Setup Checklist

- [ ] JDK 21+ installed
- [ ] SBT installed
- [ ] Docker installed (for workspace features)
- [ ] API keys configured (`OPENAI_API_KEY` or `ANTHROPIC_API_KEY`)
- [ ] Model selected (`LLM_MODEL=openai/gpt-4o`)
- [ ] Pre-commit hooks installed (`./hooks/install.sh`)
- [ ] Build succeeds (`sbt buildAll`)

---

## Additional Resources

- **Main README**: [README.md](README.md) - Project overview and getting started
- **Agent Documentation**: [docs/AGENTS.md](docs/AGENTS.md) - Agent framework guide
- **Migration Guide**: [docs/MIGRATION_GUIDE.md](docs/MIGRATION_GUIDE.md) - Breaking changes
- **Tool Calling Design**: [docs/tool-calling-api-design.md](docs/tool-calling-api-design.md)
- **Workspace Protocol**: [docs/workspace-agent-protocol.md](docs/workspace-agent-protocol.md)
- **Starter Kit**: [github.com/llm4s/llm4s.g8](https://github.com/llm4s/llm4s.g8)
- **Discord Community**: https://discord.gg/4uvTPn6qww
- **GitHub Issues**: https://github.com/llm4s/llm4s/issues

---

## Version History

| Date | Changes |
|------|---------|
| 2025-11-15 | Initial CLAUDE.md creation |
| 2025-11-16 | Added Phase 1.1 multi-turn conversation management |

---

**For questions or clarifications, consult the Discord community or open a GitHub issue.**
