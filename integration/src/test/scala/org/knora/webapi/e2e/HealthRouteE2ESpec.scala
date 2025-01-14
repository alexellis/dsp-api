/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import org.apache.pekko
import zio.Unsafe
import zio.ZIO

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.NANOSECONDS

import org.knora.webapi.E2ESpec
import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState

import pekko.http.scaladsl.model._
import pekko.http.scaladsl.testkit.RouteTestTimeout

/**
 * End-to-End (E2E) test specification for testing route rejections.
 */
class HealthRouteE2ESpec extends E2ESpec {
  implicit def default: RouteTestTimeout = RouteTestTimeout(
    FiniteDuration(appConfig.defaultTimeout.toNanos, NANOSECONDS),
  )

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("system", "health")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(appConfig)

  "The Health Route" should {

    "return 'OK' for state 'Running'" in {

      val request                    = Get(baseApiUrl + s"/health")
      val response: HttpResponse     = singleAwaitingRequest(request)
      val responseStr: String        = responseToString(response)
      val responseHeadersStr: String = response.headers.map(_.toString).mkString("\n")

      response.status should be(StatusCodes.OK)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "running-response",
            fileExtension = "json",
          ),
          text = responseStr,
        ),
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "response-headers",
            fileExtension = "json",
          ),
          text = responseHeadersStr,
        ),
      )
    }

    "return 'ServiceUnavailable' for state 'Stopped'" in {

      Unsafe.unsafe { implicit u =>
        runtime.unsafe
          .run(
            for {
              state <- ZIO.service[State]
              _     <- state.set(AppState.Stopped)
            } yield (),
          )
          .getOrThrow()
      }

      // appActor ! SetAppState(AppState.Stopped)

      val request                = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String    = responseToString(response)

      logger.debug(response.toString())

      response.status should be(StatusCodes.ServiceUnavailable)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "stopped-response",
            fileExtension = "json",
          ),
          text = responseStr,
        ),
      )
    }

    "return 'ServiceUnavailable' for state 'MaintenanceMode'" in {
      Unsafe.unsafe { implicit u =>
        runtime.unsafe
          .run(
            for {
              state <- ZIO.service[State]
              _     <- state.set(AppState.MaintenanceMode)
            } yield (),
          )
          .getOrThrow()
      }

      val request                = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String    = responseToString(response)

      logger.debug(response.toString())

      response.status should be(StatusCodes.ServiceUnavailable)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "maintenance-mode-response",
            fileExtension = "json",
          ),
          text = responseStr,
        ),
      )
    }

  }
}
