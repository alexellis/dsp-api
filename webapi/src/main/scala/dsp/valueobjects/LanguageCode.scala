/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.json.JsonCodec
import zio.prelude.Validation

import dsp.errors.ValidationException
import org.knora.webapi.slice.common.Value.StringValue

/**
 * LanguageCode value object.
 */
final case class LanguageCode private (value: String) extends AnyVal with StringValue

object LanguageCode { self =>
  implicit val codec: JsonCodec[LanguageCode] =
    JsonCodec[String].transformOrFail(
      value => LanguageCode.make(value).toEitherWith(e => e.head.getMessage()),
      languageCode => languageCode.value,
    )

  val DE: String = "de"
  val EN: String = "en"
  val FR: String = "fr"
  val IT: String = "it"
  val RM: String = "rm"

  val SupportedLanguageCodes: Set[String] = Set(
    DE,
    EN,
    FR,
    IT,
    RM,
  )

  def unsafeFrom(value: String): LanguageCode =
    make(value).fold(e => throw new IllegalArgumentException(e.head.getMessage), identity)

  def from(value: String): Either[String, LanguageCode] =
    make(value).toEitherWith(_.head.getMessage)

  def make(value: String): Validation[ValidationException, LanguageCode] =
    if (value.isEmpty) {
      Validation.fail(ValidationException(LanguageCodeErrorMessages.LanguageCodeMissing))
    } else if (!SupportedLanguageCodes.contains(value)) {
      Validation.fail(ValidationException(LanguageCodeErrorMessages.LanguageCodeInvalid(value)))
    } else {
      Validation.succeed(LanguageCode(value))
    }

  lazy val en: LanguageCode = LanguageCode(EN)
  lazy val de: LanguageCode = LanguageCode(DE)
  lazy val fr: LanguageCode = LanguageCode(FR)
  lazy val it: LanguageCode = LanguageCode(IT)
  lazy val rm: LanguageCode = LanguageCode(RM)
}

object LanguageCodeErrorMessages {
  val LanguageCodeMissing               = "LanguageCode cannot be empty."
  def LanguageCodeInvalid(lang: String) = s"LanguageCode '$lang' is invalid."
}
