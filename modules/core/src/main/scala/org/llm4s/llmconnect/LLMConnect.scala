package org.llm4s.llmconnect

import org.llm4s.config.ConfigKeys.LLM_MODEL
import org.llm4s.config.ConfigReader
import org.llm4s.error.ConfigurationError
import org.llm4s.llmconnect.config._
import org.llm4s.llmconnect.model._
import org.llm4s.llmconnect.provider._
import org.llm4s.types.Result
object LLMConnect {

  def getClient(config: ConfigReader): Result[LLMClient] =
    for {
      model    <- config.require(LLM_MODEL)
      provider <- ProviderConfigLoader(model, config)
      client   <- buildClient(provider)
    } yield client

  private def buildClient(config: ProviderConfig): Result[LLMClient] =
    config match {
      case cfg: OpenAIConfig =>
        if (cfg.baseUrl.contains("openrouter.ai"))
          OpenRouterClient(cfg)
        else OpenAIClient(cfg)
      case cfg: AzureConfig =>
        OpenAIClient(cfg)
      case cfg: AnthropicConfig =>
        AnthropicClient(cfg)
      case cfg: OllamaConfig =>
        OllamaClient(cfg)
    }

  // Typed-config entry: build client directly from ProviderConfig
  def getClient(config: ProviderConfig): Result[LLMClient] =
    buildClient(config)

  def getClient(provider: LLMProvider, config: ProviderConfig): Result[LLMClient] =
    (provider, config) match {
      case (LLMProvider.OpenAI, cfg: OpenAIConfig)       => OpenAIClient(cfg)
      case (LLMProvider.OpenRouter, cfg: OpenAIConfig)   => OpenRouterClient(cfg)
      case (LLMProvider.Azure, cfg: AzureConfig)         => OpenAIClient(cfg)
      case (LLMProvider.Anthropic, cfg: AnthropicConfig) => AnthropicClient(cfg)
      case (LLMProvider.Ollama, cfg: OllamaConfig)       => OllamaClient(cfg)
      case (prov, wrongCfg) =>
        val cfgType = wrongCfg.getClass.getSimpleName
        val msg     = s"Invalid config type $cfgType for provider $prov"
        Left(ConfigurationError(msg))
    }

  def complete(
    messages: Seq[Message],
    options: CompletionOptions = CompletionOptions()
  )(config: ConfigReader): Result[Completion] =
    getClient(config).flatMap(_.complete(Conversation(messages), options))

  // Convenience: build client from environment/config using typed ProviderConfig loader
  def fromEnv(): Result[LLMClient] =
    ConfigReader.Provider().flatMap(getClient)

}
