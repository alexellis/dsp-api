/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.asStreamAlways
import sttp.client3.basicRequest
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.Clock
import zio.Ref
import zio.Scope
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer
import zio.http.Body
import zio.http.Client
import zio.http.Header
import zio.http.Headers
import zio.http.MediaType
import zio.http.Request
import zio.http.URL
import zio.macros.accessible
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZSink

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.routing.Jwt
import org.knora.webapi.routing.JwtService
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

@accessible
trait DspIngestClient {
  def exportProject(shortcode: Shortcode): ZIO[Scope, Throwable, Path]

  def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path]
}

final case class DspIngestClientLive(
  jwtService: JwtService,
  dspIngestConfig: DspIngestConfig,
  sttpBackend: SttpBackend[Task, ZioStreams],
  tokenRef: Ref[Option[Jwt]]
) extends DspIngestClient {

  private def projectsPath(shortcode: Shortcode) = s"${dspIngestConfig.baseUrl}/projects/${shortcode.value}"

  private val getJwtString: UIO[String] = for {
    // check the current token and create a new one if:
    // * it is not present
    // * it is expired or close to expiring within the next 10 seconds
    threshold <- Clock.currentTime(TimeUnit.SECONDS).map(_ - 10)
    token <- tokenRef.get.flatMap {
               case Some(jwt) if jwt.expiration <= threshold => ZIO.succeed(jwt)
               case _                                        => jwtService.createJwtForDspIngest().tap(jwt => tokenRef.set(Some(jwt)))
             }
  } yield token.jwtString

  private val authenticatedRequest = getJwtString.map(basicRequest.auth.bearer(_))

  def exportProject(shortcode: Shortcode): ZIO[Scope, Throwable, Path] =
    for {
      tempDir   <- Files.createTempDirectoryScoped(Some("export"), List.empty)
      exportFile = tempDir / "export.zip"
      request <- authenticatedRequest.map {
                   _.post(uri"${projectsPath(shortcode)}/export")
                     .readTimeout(30.minutes)
                     .response(asStreamAlways(ZioStreams)(_.run(ZSink.fromFile(exportFile.toFile))))
                 }
      response <- request.send(backend = sttpBackend)
      _        <- ZIO.logInfo(s"Response from ingest :${response.code}")
    } yield exportFile

  def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path] = ZIO.scoped {
    for {
      importUrl <- ZIO.fromEither(URL.decode(s"${projectsPath(shortcode)}/import"))
      token     <- jwtService.createJwtForDspIngest()
      request = Request
                  .post(importUrl, Body.fromFile(fileToImport.toFile))
                  .addHeaders(
                    Headers(
                      Header.Authorization.Bearer(token.jwtString),
                      Header.ContentType(MediaType.application.zip)
                    )
                  )
      response     <- Client.request(request).provideSomeLayer[Scope](Client.default)
      bodyAsString <- response.body.asString
      _            <- ZIO.logInfo(s"Response code: ${response.status} body $bodyAsString")
    } yield fileToImport
  }
}

object DspIngestClientLive {
  val layer =
    HttpClientZioBackend.layer().orDie >+>
      ZLayer.fromZIO(Ref.make[Option[Jwt]](None)) >>>
      ZLayer.derive[DspIngestClientLive]
}
