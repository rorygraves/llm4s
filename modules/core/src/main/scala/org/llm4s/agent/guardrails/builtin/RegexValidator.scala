package org.llm4s.agent.guardrails.builtin

import org.llm4s.agent.guardrails.{ InputGuardrail, OutputGuardrail }
import org.llm4s.error.ValidationError
import org.llm4s.types.Result

import scala.util.matching.Regex

/**
 * Validates that content matches a regular expression.
 *
 * Can be used for both input and output validation.
 * Useful for enforcing format requirements like email addresses,
 * phone numbers, or custom patterns.
 *
 * @param pattern The regex pattern to match
 * @param errorMessage Optional custom error message
 */
class RegexValidator(
  pattern: Regex,
  errorMessage: Option[String] = None
) extends InputGuardrail
    with OutputGuardrail {

  def validate(value: String): Result[String] =
    if (pattern.findFirstIn(value).isDefined) {
      Right(value)
    } else {
      Left(
        ValidationError.invalid(
          "value",
          errorMessage.getOrElse(s"Value does not match pattern: $pattern")
        )
      )
    }

  val name: String = "RegexValidator"

  override val description: Option[String] = Some(
    s"Validates against pattern: $pattern"
  )

  // Resolve conflicting transform methods from both traits
  override def transform(input: String): String = input
}

object RegexValidator {

  /**
   * Create a regex validator from a pattern string.
   */
  def apply(pattern: String): RegexValidator =
    new RegexValidator(pattern.r)

  /**
   * Create a regex validator from a Regex object.
   */
  def apply(pattern: Regex): RegexValidator =
    new RegexValidator(pattern)

  /**
   * Create a regex validator with custom error message.
   */
  def apply(pattern: String, errorMessage: String): RegexValidator =
    new RegexValidator(pattern.r, Some(errorMessage))

  /**
   * Validator for email addresses (basic pattern).
   */
  def email: RegexValidator = new RegexValidator(
    "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".r,
    Some("Invalid email address format")
  )

  /**
   * Validator for phone numbers (basic pattern).
   */
  def phone: RegexValidator = new RegexValidator(
    "^\\+?[0-9]{10,15}$".r,
    Some("Invalid phone number format")
  )

  /**
   * Validator for alphanumeric content only.
   */
  def alphanumeric: RegexValidator = new RegexValidator(
    "^[A-Za-z0-9]+$".r,
    Some("Content must be alphanumeric")
  )
}
