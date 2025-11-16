package org.llm4s.agent.guardrails.builtin

import org.llm4s.agent.guardrails.OutputGuardrail
import org.llm4s.error.ValidationError
import org.llm4s.types.Result

import scala.util.{ Failure, Success, Try }

/**
 * Validates that output is valid JSON matching an optional schema.
 *
 * This guardrail ensures that LLM output is properly formatted JSON,
 * which is useful when requesting structured data from the agent.
 *
 * @param schema Optional JSON schema to validate against (future enhancement)
 */
class JSONValidator(schema: Option[ujson.Value] = None) extends OutputGuardrail {

  def validate(value: String): Result[String] =
    // Try to parse as JSON
    Try(ujson.read(value)) match {
      case Success(_) =>
        // TODO: Implement JSON schema validation if schema provided
        // For now, just check that it parses
        schema match {
          case Some(_) =>
            // Future enhancement: validate against schema
            Right(value)
          case None =>
            Right(value)
        }

      case Failure(ex) =>
        Left(
          ValidationError.invalid(
            "output",
            s"Output is not valid JSON: ${ex.getMessage}"
          )
        )
    }

  val name: String = "JSONValidator"

  override val description: Option[String] = Some(
    schema match {
      case Some(_) => "Validates output is valid JSON matching schema"
      case None    => "Validates output is valid JSON"
    }
  )
}

object JSONValidator {

  /**
   * Create a JSON validator without schema validation.
   */
  def apply(): JSONValidator = new JSONValidator()

  /**
   * Create a JSON validator with schema validation.
   * Note: Schema validation is a future enhancement.
   */
  def withSchema(schema: ujson.Value): JSONValidator =
    new JSONValidator(Some(schema))
}
