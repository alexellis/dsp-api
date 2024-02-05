/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.Gen
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.V2
import org.knora.webapi.slice.admin.domain.model.ListProperties.*

/**
 * This spec is used to test the [[List]] value objects creation.
 */
object ListPropertiesSpec extends ZIOSpecDefault {
  def spec: Spec[Any, Any] =
    suite("ListProperties")(listIriSuite, listNameSuite, positionSuite, labelsTest, commentsTest)

  private val listIriSuite = suite("ListIri")(
    test("pass an empty value and return an error") {
      assertTrue(ListIri.from("") == Left("List IRI cannot be empty."))
    },
    test("pass an invalid value and return an error") {
      val invalid = "ftp://rdfh.ch/lists/0803/qBCJAdzZSCqC_2snW5Q7Nw"
      assertTrue(ListIri.from(invalid) == Left("List IRI is invalid"))
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      val invalid = "http://rdfh.ch/lists/0803/6_xROK_UN1S2ZVNSzLlSXQ"
      assertTrue(ListIri.from(invalid) == Left(IriErrorMessages.UuidVersionInvalid))
    },
    test("pass a valid value and successfully create value object") {
      val valid = "http://rdfh.ch/lists/0803/qBCJAdzZSCqC_2snW5Q7Nw"
      assertTrue(ListIri.from(valid).map(_.value) == Right(valid))
    }
  )

  private val listNameSuite = suite("ListName")(
    test("pass an empty value and return an error") {
      assertTrue(ListName.from("") == Left("List name cannot be empty."))
    },
    test("pass an invalid value and return an error") {
      assertTrue(ListName.from("Invalid list name\r") == Left("List name is invalid."))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(ListName.from("Valid list name").map(_.value) == Right("Valid list name"))
    }
  )

  private val positionSuite = suite("Position")(
    test("should be greater than or equal -1") {
      check(Gen.int(-10, 10)) { i =>
        val actual = Position.from(i)
        i match {
          case i if i >= -1 => assertTrue(actual.map(_.value) == Right(i))
          case _ =>
            assertTrue(
              actual == Left("Invalid position value is given. Position should be either a positive value, 0 or -1.")
            )
        }
      }
    }
  )

  private val labelsTest = suite("Labels")(
    test("pass an empty object and return an error") {
      assertTrue(Labels.from(Seq.empty) == Left("At least one label needs to be supplied."))
    },
    test("pass an invalid object and return an error") {
      val invalid = Seq(V2.StringLiteralV2(value = "Invalid list label \r", language = Some("en")))
      assertTrue(Labels.from(invalid) == Left("Invalid label."))
    },
    test("pass a valid object and successfully create value object") {
      val valid = Seq(V2.StringLiteralV2(value = "Valid list label", language = Some("en")))
      assertTrue(Labels.from(valid).map(_.value) == Right(valid))
    }
  )

  private val commentsTest = suite("Comments")(
    test("pass an empty object and return an error") {
      assertTrue(Comments.from(Seq.empty) == Left("At least one comment needs to be supplied."))
    },
    test("pass an invalid object and return an error") {
      val invalid = Seq(V2.StringLiteralV2(value = "Invalid list comment \r", language = Some("en")))
      assertTrue(Comments.from(invalid) == Left("Invalid comment."))
    },
    test("pass a valid object and successfully create value object") {
      val valid = Seq(V2.StringLiteralV2(value = "Valid list comment", language = Some("en")))
      assertTrue(Comments.from(valid).map(_.value) == Right(valid))
    }
  )
}