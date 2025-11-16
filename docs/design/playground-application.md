# LLM4S Playground Application - Design Document

> **Project:** llm4s Interactive Playground
> **Date:** 2025-11-16
> **Status:** Design Proposal
> **Repository:** Separate repository (llm4s-playground)
> **Purpose:** Interactive web application for exploring and demonstrating llm4s capabilities

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Vision and Goals](#vision-and-goals)
3. [Technology Stack](#technology-stack)
4. [Architecture Overview](#architecture-overview)
5. [Feature Specifications](#feature-specifications)
6. [User Interface Design](#user-interface-design)
7. [Backend API Design](#backend-api-design)
8. [Security and Safety](#security-and-safety)
9. [Implementation Roadmap](#implementation-roadmap)
10. [Future Enhancements](#future-enhancements)

---

## Executive Summary

### Overview

The **LLM4S Playground** is an interactive web application that allows users to explore and experiment with all features of the llm4s framework through an intuitive tabbed interface. Unlike cloud-hosted services, the Playground runs entirely locally with user-provided API keys, ensuring privacy and control.

### Key Features

- **Tab-based Interface** - Separate tabs for each major llm4s capability
- **Multi-Provider Support** - Switch between OpenAI, Anthropic, Azure OpenAI, and Ollama
- **Interactive Examples** - Pre-built scenarios and templates for quick exploration
- **Real-time Results** - Streaming responses with visual feedback
- **Export Capabilities** - Download conversations, traces, and code snippets
- **Educational Focus** - Learn llm4s through hands-on experimentation

### Target Audience

- **Developers** evaluating llm4s for their projects
- **Students** learning about LLM application development
- **Researchers** experimenting with different models and prompts
- **Teams** prototyping agent workflows before production implementation

### Inspiration

Similar to **Szork** (https://github.com/llm4s/szork) - a game demonstrating llm4s features - the Playground uses:
- **Scala backend** (Play Framework or http4s)
- **Vue.js + Vuetify frontend**
- **Local deployment** with user-provided API keys
- **No cloud hosting** - runs entirely on user's machine

---

## Vision and Goals

### Primary Goals

1. **Comprehensive Feature Coverage**
   - Demonstrate ALL llm4s capabilities in one application
   - Provide hands-on experience with each feature
   - Show best practices and common patterns

2. **Educational Value**
   - Teach users how to use llm4s effectively
   - Provide copy-paste ready code examples
   - Explain design decisions and trade-offs

3. **Developer Experience**
   - Intuitive UI requiring minimal learning curve
   - Fast feedback loops for experimentation
   - Easy setup with clear documentation

4. **Production Readiness Preview**
   - Show what's possible with llm4s
   - Demonstrate performance and reliability
   - Highlight integration points

### Non-Goals

- **Cloud Hosting** - Not a SaaS product, runs locally only
- **User Authentication** - Single-user application
- **Data Persistence** - Sessions are ephemeral (optional export)
- **Production Deployment** - Development and learning tool only

---

## Technology Stack

### Backend

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| **Language** | Scala 3.7.1 | Native llm4s integration, type safety |
| **Web Framework** | Play Framework 3.0 or http4s 0.23 | Async, modern, WebSocket support |
| **JSON** | uPickle | Consistent with llm4s core |
| **Concurrency** | Cats Effect 3 | Functional effects, streaming |
| **Configuration** | Typesafe Config | Standard Scala config management |
| **Testing** | ScalaTest + Scalamock | Quality assurance |

**Framework Choice:**
- **Play Framework** - More batteries-included, easier WebSocket, good for rapid development
- **http4s** - More functional, composable, better for learning FP patterns

**Recommendation:** Start with Play Framework for faster initial development, can migrate to http4s later if desired.

### Frontend

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| **Framework** | Vue.js 3 | Reactive, component-based, easy to learn |
| **UI Library** | Vuetify 3 | Material Design, rich components |
| **State Management** | Pinia | Modern Vue state management |
| **HTTP Client** | Axios | WebSocket + REST support |
| **Build Tool** | Vite | Fast development, HMR |
| **Markdown** | markdown-it | Display formatted responses |
| **Code Display** | Prism.js or Highlight.js | Syntax highlighting for code examples |

### Infrastructure

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| **Build Tool** | SBT | Standard Scala build tool |
| **Package Manager** | npm/pnpm | Frontend dependencies |
| **Docker** | Optional | Workspace features (containerized execution) |
| **Documentation** | Markdown + VuePress | Integrated documentation |

---

## Architecture Overview

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       User's Machine                         │
│                                                               │
│  ┌─────────────────┐                 ┌──────────────────┐   │
│  │   Vue Frontend  │◄────WebSocket───►│  Scala Backend  │   │
│  │   (Vuetify)     │    + REST API    │  (Play/http4s)  │   │
│  │                 │                  │                  │   │
│  │  - Tabs UI      │                  │  - LLM4S Core   │   │
│  │  - Real-time    │                  │  - API Routes   │   │
│  │  - Visualizations│                 │  - Streaming    │   │
│  └─────────────────┘                  └────────┬─────────┘   │
│                                                │              │
│                                                │              │
│  ┌─────────────────────────────────────────────▼──────────┐  │
│  │           llm4s Framework (modules/core)               │  │
│  │                                                         │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │  │
│  │  │LLMConnect│  │  Agent   │  │  Tools   │  │  RAG   │ │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └────────┘ │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │  │
│  │  │Multimodal│  │  Trace   │  │  MCP     │  │ Speech │ │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └────────┘ │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                │              │
└────────────────────────────────────────────────┼──────────────┘
                                                 │
                                                 ▼
                                    ┌────────────────────────┐
                                    │   External Services    │
                                    │  - OpenAI API          │
                                    │  - Anthropic API       │
                                    │  - Azure OpenAI        │
                                    │  - Ollama (local)      │
                                    │  - Langfuse (tracing)  │
                                    └────────────────────────┘
```

### Application Layers

#### 1. Frontend Layer (Vue + Vuetify)

**Responsibilities:**
- Render tabbed interface
- Handle user interactions
- Display streaming responses
- Show visualizations (traces, graphs, etc.)
- Manage local UI state

**Key Components:**
- `App.vue` - Main application shell
- `TabsContainer.vue` - Tab navigation
- `ProviderSelector.vue` - Model/provider selection
- `[Feature]Tab.vue` - Individual feature tabs
- `CodeExample.vue` - Syntax-highlighted code snippets
- `StreamingOutput.vue` - Real-time response display
- `TraceViewer.vue` - Execution trace visualization

#### 2. Backend Layer (Scala)

**Responsibilities:**
- Expose REST + WebSocket APIs
- Execute llm4s operations
- Stream responses to frontend
- Manage active sessions
- Handle errors gracefully

**Key Modules:**
- `routes/` - HTTP/WebSocket endpoints
- `services/` - Business logic (wrapping llm4s)
- `models/` - Request/response DTOs
- `streaming/` - WebSocket streaming handlers
- `config/` - Configuration management

#### 3. llm4s Integration Layer

**Responsibilities:**
- Provide type-safe llm4s operations
- Convert between llm4s and API types
- Handle Result → HTTP response mapping
- Manage llm4s client lifecycle

**Key Services:**
- `LLMService` - LLMConnect integration
- `AgentService` - Agent execution
- `ToolService` - Tool management
- `RAGService` - Embeddings and vector search
- `MultimodalService` - Image/speech processing
- `TracingService` - Observability

---

## Feature Specifications

### Tab-Based Feature Organization

Each major llm4s capability gets its own tab in the UI. Below is the detailed specification for each tab.

---

### Tab 1: **Chat Playground**

**Purpose:** Basic multi-turn conversations with LLMs.

#### Features

1. **Provider Selection**
   - Dropdown: OpenAI, Anthropic, Azure OpenAI, Ollama
   - Model selection (filtered by provider)
   - API key input (stored in local session only)

2. **Conversation Interface**
   - Chat-style UI (messages left/right aligned)
   - System prompt configuration
   - Temperature, max tokens sliders
   - Streaming toggle

3. **Advanced Options**
   - Context window management (manual pruning)
   - Token counter (show current usage)
   - Export conversation (JSON, markdown)

4. **Code Example Panel**
   - Show equivalent Scala code for current conversation
   - Copy-paste ready snippets
   - Updates as user interacts

#### Backend API

```scala
// REST endpoint
POST /api/chat/complete
{
  "provider": "openai",
  "model": "gpt-4o",
  "messages": [...],
  "options": {
    "temperature": 0.7,
    "maxTokens": 1000
  }
}

// WebSocket endpoint
WS /api/chat/stream
// Real-time bidirectional communication
```

#### UI Components

- `ChatPlayground.vue`
- `MessageList.vue`
- `MessageInput.vue`
- `ProviderSelector.vue`
- `ChatOptions.vue`
- `CodeExample.vue`

---

### Tab 2: **Tool Calling**

**Purpose:** Demonstrate function calling and tool execution.

#### Features

1. **Built-in Tools**
   - Weather tool (demo)
   - Calculator (demo)
   - Web search (if API key provided)
   - File system (sandboxed)
   - Custom tool definition

2. **Custom Tool Builder**
   - Name, description input
   - Parameter schema builder (JSON Schema UI)
   - Implementation code editor
   - Test tool execution

3. **Tool Execution Viewer**
   - Show when tools are called
   - Display arguments passed
   - Show tool results
   - Visualize tool call flow

4. **Code Examples**
   - How to define tools in llm4s
   - Tool registry setup
   - Error handling patterns

#### Backend API

```scala
POST /api/tools/execute
{
  "toolName": "getWeather",
  "toolRegistry": [...],
  "query": "What's the weather in London?",
  "provider": "anthropic"
}

// Custom tool definition
POST /api/tools/define
{
  "name": "customTool",
  "description": "...",
  "schema": {...},
  "implementation": "def execute(...)"
}
```

#### UI Components

- `ToolCallingTab.vue`
- `ToolRegistry.vue`
- `ToolBuilder.vue`
- `ToolExecutionLog.vue`
- `SchemaBuilder.vue`

---

### Tab 3: **Single Agent**

**Purpose:** Execute single-agent workflows with multiple steps.

#### Features

1. **Agent Configuration**
   - System instructions
   - Tool selection
   - Max steps configuration
   - Debug mode toggle

2. **Execution Visualization**
   - Step-by-step progress
   - Current agent status
   - Tool calls per step
   - Reasoning/thinking display

3. **Trace Viewer**
   - Markdown trace log display
   - Timeline visualization
   - Token usage per step
   - Export trace

4. **Example Scenarios**
   - Research agent (web search + summarization)
   - Data analysis agent (file + calculator tools)
   - Code assistant agent
   - Custom scenario builder

#### Backend API

```scala
POST /api/agent/run
{
  "query": "Research the latest AI news",
  "systemInstructions": "...",
  "tools": [...],
  "maxSteps": 10,
  "debug": true
}

WS /api/agent/stream
// Stream agent steps in real-time
```

#### UI Components

- `SingleAgentTab.vue`
- `AgentConfig.vue`
- `AgentStepViewer.vue`
- `TraceTimeline.vue`
- `ScenarioSelector.vue`

---

### Tab 4: **Multi-Agent Orchestration**

**Purpose:** Demonstrate DAG-based multi-agent workflows.

#### Features

1. **Workflow Builder**
   - Visual DAG editor
   - Drag-and-drop agent nodes
   - Connect agents with typed edges
   - Configure parallel/sequential execution

2. **Pre-built Workflows**
   - Content pipeline (research → write → review)
   - Data processing (extract → transform → load)
   - Customer support (triage → resolve → follow-up)
   - Custom workflow templates

3. **Execution Viewer**
   - Real-time DAG visualization
   - Node status (pending, running, complete, failed)
   - Data flow between agents
   - Parallel execution visualization

4. **Concurrency Control**
   - Max concurrent nodes slider
   - Batch execution mode
   - Cancellation support

#### Backend API

```scala
POST /api/orchestration/run
{
  "workflow": {
    "nodes": [
      {"id": "research", "agent": {...}},
      {"id": "summarize", "agent": {...}}
    ],
    "edges": [
      {"from": "research", "to": "summarize"}
    ]
  },
  "input": "...",
  "maxConcurrentNodes": 3
}

WS /api/orchestration/stream
// Stream workflow execution events
```

#### UI Components

- `MultiAgentTab.vue`
- `WorkflowBuilder.vue`
- `DAGVisualization.vue`
- `WorkflowTemplates.vue`
- `ExecutionGraph.vue`

---

### Tab 5: **RAG (Embeddings & Search)**

**Purpose:** Demonstrate retrieval-augmented generation.

#### Features

1. **Document Management**
   - Upload documents (text, PDF, markdown)
   - Chunking configuration (size, overlap)
   - View chunks

2. **Embedding Generation**
   - Provider selection (OpenAI, Anthropic)
   - Model selection
   - Batch embedding
   - Progress visualization

3. **Semantic Search**
   - Query input
   - Top-K slider
   - Similarity threshold
   - Visualize results (similarity scores)

4. **RAG Pipeline**
   - Full RAG workflow: query → retrieve → augment → generate
   - Context window integration
   - Source attribution

#### Backend API

```scala
POST /api/rag/embed
{
  "documents": [...],
  "provider": "openai",
  "chunkSize": 500,
  "chunkOverlap": 50
}

POST /api/rag/search
{
  "query": "What is llm4s?",
  "topK": 5,
  "threshold": 0.7
}

POST /api/rag/query
{
  "query": "Explain the agent framework",
  "retrievalConfig": {...},
  "generationConfig": {...}
}
```

#### UI Components

- `RAGTab.vue`
- `DocumentUploader.vue`
- `EmbeddingConfig.vue`
- `SemanticSearch.vue`
- `RAGPipeline.vue`
- `SimilarityVisualization.vue`

---

### Tab 6: **Multimodal**

**Purpose:** Image generation, vision, and speech capabilities.

#### Sub-tabs

##### 6a. Image Generation

**Features:**
- Provider selection (Stable Diffusion, Hugging Face)
- Prompt input
- Size selection (512x512, 1024x1024, etc.)
- Negative prompts
- Advanced settings (seed, guidance scale, steps)
- Gallery view of generated images
- Download images

**Backend API:**
```scala
POST /api/multimodal/image/generate
{
  "provider": "stable-diffusion",
  "prompt": "A serene mountain landscape",
  "negativePrompt": "blurry, low quality",
  "size": "1024x1024",
  "seed": 42
}
```

##### 6b. Vision (Image Analysis)

**Features:**
- Upload image
- Provider selection (OpenAI Vision, Claude Vision)
- Query about image
- Bounding box visualization (if supported)
- Multi-image analysis

**Backend API:**
```scala
POST /api/multimodal/vision/analyze
{
  "provider": "anthropic",
  "imageUrl": "...",
  "query": "What's in this image?"
}
```

##### 6c. Speech (STT/TTS)

**Features:**
- **Speech-to-Text:**
  - Upload audio file
  - Provider selection (Vosk, Whisper)
  - Transcription display

- **Text-to-Speech:**
  - Text input
  - Voice selection
  - Play audio in browser
  - Download audio file

**Backend API:**
```scala
POST /api/multimodal/speech/transcribe
{
  "provider": "whisper",
  "audioFile": "..."
}

POST /api/multimodal/speech/synthesize
{
  "provider": "tacotron2",
  "text": "Hello, world!",
  "voice": "default"
}
```

#### UI Components

- `MultimodalTab.vue`
- `ImageGeneration.vue`
- `VisionAnalysis.vue`
- `SpeechSTT.vue`
- `SpeechTTS.vue`
- `ImageGallery.vue`

---

### Tab 7: **MCP Integration**

**Purpose:** Demonstrate Model Context Protocol integration.

#### Features

1. **MCP Server Configuration**
   - Server URL input
   - Transport selection (stdio, HTTP)
   - Connection status
   - Available tools list

2. **MCP Tool Registry**
   - List tools from connected MCP server
   - Tool schema inspection
   - Test tool execution

3. **Example: Playwright Integration**
   - Connect to Playwright MCP server
   - Browser automation tools
   - Screenshot capture
   - Web scraping demo

4. **Custom MCP Servers**
   - Instructions for creating MCP servers
   - Example MCP server templates

#### Backend API

```scala
POST /api/mcp/connect
{
  "serverUrl": "http://localhost:3000",
  "transport": "http"
}

GET /api/mcp/tools
// List available MCP tools

POST /api/mcp/execute
{
  "toolName": "playwright.screenshot",
  "parameters": {...}
}
```

#### UI Components

- `MCPTab.vue`
- `MCPServerConfig.vue`
- `MCPToolList.vue`
- `MCPToolExecutor.vue`
- `PlaywrightDemo.vue`

---

### Tab 8: **Tracing & Observability**

**Purpose:** Visualize execution traces and observability data.

#### Features

1. **Tracing Backend Selection**
   - Console (local logs)
   - Langfuse (cloud tracing)
   - Markdown (file export)

2. **Trace Viewer**
   - Timeline visualization
   - Span hierarchy
   - Token usage tracking
   - Latency analysis

3. **Live Tracing**
   - Real-time trace updates during execution
   - Filter by event type
   - Search traces

4. **Analytics**
   - Token usage over time
   - Cost estimation
   - Performance metrics

#### Backend API

```scala
GET /api/trace/list
// List recent traces

GET /api/trace/:traceId
// Get detailed trace

WS /api/trace/live
// Stream traces in real-time
```

#### UI Components

- `TracingTab.vue`
- `TraceViewer.vue`
- `TraceTimeline.vue`
- `TraceAnalytics.vue`
- `LiveTraceStream.vue`

---

### Tab 9: **Workspace Isolation**

**Purpose:** Demonstrate containerized tool execution (Docker-based).

#### Features

1. **Workspace Management**
   - Create workspace
   - View workspace files
   - Execute commands in workspace
   - File operations (read, write, modify)

2. **Safe Tool Execution**
   - Run shell commands in sandbox
   - File system exploration
   - Search workspace files
   - Workspace metadata

3. **Security Visualization**
   - Show isolation boundaries
   - Resource limits
   - Safety constraints

4. **Example Workflows**
   - Code analysis in sandbox
   - Data processing in isolated environment
   - Testing untrusted code safely

#### Backend API

```scala
POST /api/workspace/create
{
  "workspaceId": "demo-workspace"
}

POST /api/workspace/execute
{
  "workspaceId": "demo-workspace",
  "tool": "executeCommand",
  "parameters": {
    "command": "ls -la"
  }
}
```

#### UI Components

- `WorkspaceTab.vue`
- `WorkspaceExplorer.vue`
- `WorkspaceTerminal.vue`
- `WorkspaceFiles.vue`
- `SecurityVisualization.vue`

---

### Tab 10: **Configuration & Settings**

**Purpose:** Global application settings and configuration.

#### Features

1. **API Keys Management**
   - OpenAI API key
   - Anthropic API key
   - Azure OpenAI credentials
   - Hugging Face token
   - Langfuse credentials
   - Brave Search API key
   - Note: Keys stored in browser session only, never persisted

2. **Default Provider Settings**
   - Default provider selection
   - Default model
   - Default temperature
   - Default max tokens

3. **UI Preferences**
   - Theme (light/dark)
   - Code editor settings
   - Font size
   - Auto-scroll

4. **Export/Import**
   - Export all settings (JSON)
   - Import settings
   - Reset to defaults

#### Backend API

```scala
GET /api/config
// Get current configuration

POST /api/config
// Update configuration (session only)
```

#### UI Components

- `SettingsTab.vue`
- `APIKeyManager.vue`
- `DefaultSettings.vue`
- `UIPreferences.vue`
- `ExportImport.vue`

---

### Tab 11: **Code Examples & Documentation**

**Purpose:** Learn llm4s through comprehensive examples.

#### Features

1. **Example Browser**
   - Categorized examples (Basic, Agents, RAG, etc.)
   - Search examples
   - Filter by feature

2. **Interactive Examples**
   - Step-by-step tutorials
   - Editable code snippets
   - Run examples directly in playground

3. **Code Templates**
   - Project scaffolding
   - Common patterns
   - Best practices

4. **Documentation Links**
   - Link to llm4s docs
   - API reference
   - GitHub repository

#### UI Components

- `ExamplesTab.vue`
- `ExampleBrowser.vue`
- `InteractiveTutorial.vue`
- `CodeTemplates.vue`
- `DocumentationLinks.vue`

---

## User Interface Design

### Overall Layout

```
┌──────────────────────────────────────────────────────────────┐
│  LLM4S Playground                    [Provider: OpenAI ▼]    │
├──────────────────────────────────────────────────────────────┤
│ ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐                          │
│ │Ch│To│Ag│MA│RA│Mu│MC│Tr│Wo│Se│Ex│  ◄─── Tabs               │
│ └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘                          │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────┐  ┌───────────────────────────┐ │
│  │                         │  │                           │ │
│  │   Main Content Area     │  │   Code Example Panel     │ │
│  │   (Tab-specific UI)     │  │   (Scala snippets)       │ │
│  │                         │  │                           │ │
│  │                         │  │   [Copy Code]            │ │
│  │                         │  │                           │ │
│  └─────────────────────────┘  └───────────────────────────┘ │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│  Status: Ready │ Tokens: 1,234 │ Cost: $0.05 │ Latency: 1.2s │
└──────────────────────────────────────────────────────────────┘
```

### Design Principles

1. **Responsive Layout**
   - Desktop-first (primary use case)
   - Mobile-friendly for demos
   - Collapsible side panels

2. **Consistent Navigation**
   - Sticky tab bar
   - Breadcrumbs for sub-sections
   - Back/forward navigation

3. **Visual Feedback**
   - Loading indicators
   - Progress bars for long operations
   - Success/error toasts
   - Real-time status updates

4. **Accessibility**
   - Keyboard navigation
   - Screen reader support
   - High contrast mode
   - Focus indicators

### Color Scheme (Vuetify Material Design)

**Light Theme:**
- Primary: Indigo (#3F51B5)
- Secondary: Deep Orange (#FF5722)
- Success: Green (#4CAF50)
- Error: Red (#F44336)
- Background: White (#FFFFFF)
- Surface: Grey 100 (#F5F5F5)

**Dark Theme:**
- Primary: Light Blue (#03A9F4)
- Secondary: Amber (#FFC107)
- Success: Green (#4CAF50)
- Error: Red (#F44336)
- Background: Grey 900 (#212121)
- Surface: Grey 800 (#424242)

---

## Backend API Design

### REST API Structure

```
/api
├── /chat
│   ├── POST /complete          # Single completion
│   └── GET  /history           # Conversation history
├── /tools
│   ├── POST /execute           # Execute tool
│   ├── POST /define            # Define custom tool
│   └── GET  /list              # List available tools
├── /agent
│   ├── POST /run               # Run single agent
│   └── GET  /status/:id        # Get agent status
├── /orchestration
│   ├── POST /run               # Run multi-agent workflow
│   └── GET  /status/:id        # Get workflow status
├── /rag
│   ├── POST /embed             # Generate embeddings
│   ├── POST /search            # Semantic search
│   └── POST /query             # RAG query
├── /multimodal
│   ├── /image
│   │   ├── POST /generate      # Generate image
│   │   └── POST /analyze       # Vision analysis
│   └── /speech
│       ├── POST /transcribe    # STT
│       └── POST /synthesize    # TTS
├── /mcp
│   ├── POST /connect           # Connect to MCP server
│   ├── GET  /tools             # List MCP tools
│   └── POST /execute           # Execute MCP tool
├── /workspace
│   ├── POST /create            # Create workspace
│   ├── POST /execute           # Execute workspace command
│   └── GET  /files             # List workspace files
├── /trace
│   ├── GET  /list              # List traces
│   └── GET  /:traceId          # Get trace details
└── /config
    ├── GET  /                  # Get config
    └── POST /                  # Update config
```

### WebSocket Endpoints

```
/ws
├── /chat/stream                # Streaming chat
├── /agent/stream               # Streaming agent execution
├── /orchestration/stream       # Streaming workflow execution
└── /trace/live                 # Live trace updates
```

### Request/Response Types

All requests/responses use JSON with uPickle serialization.

**Common Response Envelope:**
```scala
sealed trait APIResponse[+A]
case class Success[A](data: A, meta: Option[ResponseMeta] = None) extends APIResponse[A]
case class Error(error: ErrorDetail) extends APIResponse[Nothing]

case class ErrorDetail(
  code: String,
  message: String,
  details: Option[ujson.Value] = None
)

case class ResponseMeta(
  requestId: String,
  timestamp: Long,
  duration: Long
)
```

**Streaming Response:**
```scala
sealed trait StreamEvent
case class DataEvent(data: ujson.Value) extends StreamEvent
case class StatusEvent(status: String) extends StreamEvent
case class ErrorEvent(error: ErrorDetail) extends StreamEvent
case object CompleteEvent extends StreamEvent
```

---

## Security and Safety

### Local-Only Architecture

**No Cloud Components:**
- All processing happens locally
- No data sent to playground servers (doesn't exist)
- API keys never leave user's machine

**Session Storage:**
- API keys stored in browser session storage
- Cleared when browser closes
- Never persisted to disk
- Warning displayed about key security

### API Key Management

**Best Practices:**
```
⚠️ Security Warning
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Your API keys are stored in your browser's session
storage and are NEVER sent to any server except the
official LLM provider APIs (OpenAI, Anthropic, etc.).

Recommendations:
• Use API keys with restricted permissions
• Set spending limits on your API accounts
• Clear keys when finished using the playground
• Do not use the playground on shared computers
```

### Workspace Isolation

**Docker Security:**
- Read-only file system (except designated workspace dirs)
- No network access from containers
- Resource limits (CPU, memory, disk)
- Short-lived containers (auto-cleanup)

**File System Safety:**
- Workspace operations limited to designated directories
- No access to host file system
- File size limits
- Malware scanning (optional, if integrated)

### Input Validation

**Backend Validation:**
- Request size limits
- Rate limiting per session
- Input sanitization (prevent injection)
- Schema validation (JSON Schema)

**Frontend Validation:**
- Client-side validation before submission
- Display validation errors clearly
- Prevent malformed requests

---

## Implementation Roadmap

### Phase 1: Foundation (Weeks 1-4)

**Goal:** Basic infrastructure and core tabs.

#### Week 1: Project Setup
- [ ] Initialize repository (llm4s-playground)
- [ ] Setup SBT project structure
- [ ] Setup Vue + Vite frontend
- [ ] Configure Play Framework (or http4s)
- [ ] Basic REST API skeleton
- [ ] Hello World integration test

#### Week 2: Provider Integration
- [ ] Provider selector UI
- [ ] LLMConnect service wrapper
- [ ] Configuration management
- [ ] API key storage (session)
- [ ] Test with OpenAI and Anthropic

#### Week 3: Chat Playground
- [ ] Chat UI (messages, input)
- [ ] Streaming support (WebSocket)
- [ ] Basic completions API
- [ ] Code example generation
- [ ] Export conversation

#### Week 4: Tool Calling Tab
- [ ] Tool registry UI
- [ ] Built-in demo tools (weather, calculator)
- [ ] Tool execution visualization
- [ ] Tool calling API integration

**Deliverable:** Working playground with Chat and Tool Calling tabs.

---

### Phase 2: Agent Features (Weeks 5-8)

#### Week 5: Single Agent Tab
- [ ] Agent configuration UI
- [ ] Step-by-step execution viewer
- [ ] Agent streaming API
- [ ] Trace log display
- [ ] Example scenarios

#### Week 6: Multi-Agent Orchestration
- [ ] DAG visualization component
- [ ] Workflow builder UI
- [ ] Orchestration API
- [ ] Pre-built workflow templates
- [ ] Execution graph viewer

#### Week 7: Tracing & Observability
- [ ] Trace viewer UI
- [ ] Timeline visualization
- [ ] Langfuse integration
- [ ] Live trace streaming
- [ ] Analytics dashboard

#### Week 8: Polish & Testing
- [ ] End-to-end testing
- [ ] Bug fixes
- [ ] Documentation
- [ ] Performance optimization

**Deliverable:** Full agent functionality with orchestration and tracing.

---

### Phase 3: Multimodal & Advanced (Weeks 9-12)

#### Week 9: RAG Tab
- [ ] Document uploader
- [ ] Embedding generation UI
- [ ] Semantic search UI
- [ ] RAG pipeline implementation
- [ ] Similarity visualization

#### Week 10: Multimodal Tab
- [ ] Image generation UI
- [ ] Vision analysis UI
- [ ] Image gallery component
- [ ] Stable Diffusion integration
- [ ] Claude Vision integration

#### Week 11: Speech & MCP
- [ ] STT/TTS UI
- [ ] Audio player component
- [ ] MCP server connection UI
- [ ] MCP tool registry
- [ ] Playwright demo

#### Week 12: Workspace & Final Polish
- [ ] Workspace isolation UI
- [ ] File explorer component
- [ ] Terminal emulator
- [ ] Security visualization
- [ ] Final testing and documentation

**Deliverable:** Complete playground with all features.

---

### Phase 4: Documentation & Release (Weeks 13-14)

#### Week 13: Documentation
- [ ] User guide
- [ ] Developer setup instructions
- [ ] Video tutorials
- [ ] Example gallery
- [ ] Troubleshooting guide

#### Week 14: Release Preparation
- [ ] Final testing
- [ ] Performance optimization
- [ ] Security audit
- [ ] Release packaging
- [ ] Announcement blog post

**Deliverable:** Production-ready playground, published to GitHub.

---

## Future Enhancements

### Post-Release Features

1. **Conversation Management**
   - Save/load conversations
   - Conversation history browser
   - Search conversations
   - Tag and organize

2. **Collaborative Features**
   - Share playground links (read-only)
   - Export playground state
   - Import shared experiments

3. **Advanced Visualizations**
   - Token usage charts
   - Cost analysis dashboard
   - Performance profiling
   - Model comparison tools

4. **Template Library**
   - Community-contributed templates
   - Workflow marketplace
   - Example repository

5. **Integration Plugins**
   - VS Code extension
   - IntelliJ plugin
   - Browser extension
   - CLI companion

6. **Enhanced Agent Features**
   - Guardrails framework UI (when available in llm4s)
   - Handoff mechanism visualization
   - Reasoning modes (when supported)
   - Event-based streaming (when available)

7. **Educational Content**
   - Interactive tutorials
   - Guided walkthroughs
   - Best practices guide
   - Certification path

8. **Performance Tools**
   - Benchmarking suite
   - A/B testing framework
   - Prompt optimization suggestions
   - Cost optimization recommendations

---

## Technical Considerations

### State Management

**Frontend State (Pinia):**
```javascript
// stores/playground.js
export const usePlaygroundStore = defineStore('playground', {
  state: () => ({
    currentTab: 'chat',
    provider: 'openai',
    model: 'gpt-4o',
    apiKeys: {},
    conversations: {},
    traces: [],
    settings: {}
  }),
  actions: {
    setProvider(provider, model) { ... },
    addMessage(message) { ... },
    clearConversation() { ... }
  }
})
```

**Backend State:**
- Stateless API (each request independent)
- Session management via tokens
- Active streams tracked in memory
- Cleanup on disconnect

### Performance Optimization

**Frontend:**
- Lazy load tab components
- Virtual scrolling for long lists
- Debounced input handlers
- Memoized computations
- Code splitting by route

**Backend:**
- Connection pooling for LLM clients
- Caching for embeddings (optional)
- Streaming for large responses
- Async processing with Cats Effect

### Error Handling

**User-Friendly Errors:**
```scala
sealed trait PlaygroundError extends LLMError
case class InvalidAPIKey(provider: String) extends PlaygroundError {
  override def message: String =
    s"Invalid API key for $provider. Please check your settings."
}
case class ModelNotAvailable(model: String) extends PlaygroundError
case class FeatureNotSupported(feature: String, provider: String) extends PlaygroundError
```

**Error Display:**
- Toast notifications for transient errors
- Error panels with retry options
- Detailed error logs (expandable)
- Suggestions for resolution

### Testing Strategy

**Frontend Tests:**
- Unit tests (Vitest)
- Component tests (Vue Test Utils)
- E2E tests (Playwright or Cypress)

**Backend Tests:**
- Unit tests (ScalaTest)
- Integration tests (Play/http4s test utilities)
- API contract tests

**Manual Testing:**
- Feature checklist
- Cross-browser testing
- Accessibility audit
- Performance testing

---

## Development Guidelines

### Code Style

**Scala:**
- Follow llm4s conventions
- Scalafmt for formatting
- ScalaDoc for public APIs
- Functional patterns preferred

**JavaScript/Vue:**
- ESLint + Prettier
- Composition API (Vue 3)
- TypeScript optional (recommended)
- Component documentation

### Git Workflow

**Branching:**
- `main` - stable releases
- `develop` - integration branch
- `feature/*` - feature branches
- `fix/*` - bug fixes

**Commits:**
- Conventional commits format
- Clear, descriptive messages
- Reference issue numbers

### Documentation

**README.md:**
```markdown
# LLM4S Playground

Interactive web application for exploring llm4s capabilities.

## Quick Start

1. Prerequisites: JDK 21+, Node.js 18+, Docker (optional)
2. Clone repository: `git clone ...`
3. Start backend: `sbt run`
4. Start frontend: `cd frontend && npm run dev`
5. Open browser: `http://localhost:3000`

## Features

- Chat with multiple LLM providers
- Execute single and multi-agent workflows
- Experiment with RAG and embeddings
- Generate and analyze images
- ...

## Documentation

See [docs/](docs/) for detailed documentation.
```

**User Guide (docs/USER_GUIDE.md):**
- Getting started
- Feature tutorials
- Troubleshooting
- FAQ

**Developer Guide (docs/DEVELOPER_GUIDE.md):**
- Architecture overview
- Setup instructions
- Contributing guidelines
- API reference

---

## Deployment Instructions

### Local Development

```bash
# Terminal 1: Backend
cd llm4s-playground
sbt run

# Terminal 2: Frontend
cd frontend
npm install
npm run dev

# Terminal 3: Docker (optional, for workspace features)
docker compose up
```

### Production Build

```bash
# Build frontend
cd frontend
npm run build

# Build backend with frontend assets
sbt dist

# Run packaged application
unzip target/universal/llm4s-playground-*.zip
./llm4s-playground-*/bin/llm4s-playground
```

### Docker Deployment

```dockerfile
# Dockerfile
FROM openjdk:21-slim
WORKDIR /app
COPY target/universal/llm4s-playground-*.tgz .
RUN tar -xzf llm4s-playground-*.tgz
EXPOSE 9000
CMD ["./llm4s-playground-*/bin/llm4s-playground"]
```

```bash
# Build and run
docker build -t llm4s-playground .
docker run -p 9000:9000 llm4s-playground
```

---

## Success Metrics

### User Engagement

- **Active Users:** Number of unique users per month
- **Session Duration:** Average time spent in playground
- **Feature Usage:** Which tabs are most popular
- **Return Rate:** Users returning after first session

### Educational Impact

- **Code Examples Copied:** Number of snippets copied
- **Example Completions:** Number of tutorials finished
- **Documentation Views:** Page views on docs

### Technical Metrics

- **Performance:** Page load time, API latency
- **Reliability:** Uptime, error rate
- **Quality:** Bug reports, user feedback

### Community Growth

- **GitHub Stars:** Repository popularity
- **Contributions:** Pull requests, issues
- **Social Media:** Mentions, shares

---

## Conclusion

The **LLM4S Playground** will serve as the premier interactive environment for learning and experimenting with the llm4s framework. By providing a comprehensive, user-friendly interface to all llm4s capabilities, the playground will:

1. **Accelerate Adoption** - Lower barrier to entry for new users
2. **Showcase Capabilities** - Demonstrate the power of llm4s
3. **Educate Developers** - Teach best practices through hands-on experience
4. **Gather Feedback** - Understand user needs and pain points
5. **Build Community** - Create a shared space for llm4s exploration

### Next Steps

1. **Validate Design** - Review with llm4s maintainers and community
2. **Prioritize Features** - Determine MVP scope
3. **Assign Resources** - Identify developers and timeline
4. **Setup Repository** - Initialize llm4s-playground repository
5. **Begin Implementation** - Start Phase 1 development

### Resources Required

**Development Team:**
- 1-2 Full-stack developers (Scala + Vue.js)
- 1 UI/UX designer (part-time)
- 1 Technical writer (documentation)

**Timeline:**
- **MVP (Phase 1-2):** 8 weeks
- **Full Release (Phase 1-4):** 14 weeks
- **Post-release Support:** Ongoing

**Infrastructure:**
- GitHub repository
- Documentation site (GitHub Pages or VuePress)
- Demo video hosting
- Community forum/Discord

---

**End of Design Document**
