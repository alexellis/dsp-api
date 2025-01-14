/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.testcontainers.TestContainerOps.StartableOps

final class DspIngestTestContainer extends GenericContainer[DspIngestTestContainer](s"daschswiss/dsp-ingest:latest")

object DspIngestTestContainer {

  private val assetDir = "/opt/images"
  private val tempDir  = "/opt/temp"

  def make(imagesVolume: SharedVolumes.Images): DspIngestTestContainer = {
    val port = 3340
    new DspIngestTestContainer()
      .withExposedPorts(port)
      .withEnv("SERVICE_PORT", s"$port")
      .withEnv("SERVICE_LOG_FORMAT", "text")
      .withEnv("JWT_AUDIENCE", s"http://localhost:$port")
      .withEnv("JWT_ISSUER", "0.0.0.0:3333")
      .withEnv("STORAGE_ASSET_DIR", assetDir)
      .withEnv("STORAGE_TEMP_DIR", tempDir)
      .withEnv("JWT_SECRET", "UP 4888, nice 4-8-4 steam engine")
      .withEnv("SIPI_USE_LOCAL_DEV", "false")
      .withEnv("JWT_DISABLE_AUTH", "true")
      .withFileSystemBind(imagesVolume.hostPath, assetDir, BindMode.READ_WRITE)
  }

  private val initDspIngest = ZLayer.fromZIO(
    ZIO.serviceWithZIO[DspIngestTestContainer] { it =>
      ZIO.attemptBlocking(it.execInContainer("mkdir", s"$tempDir")).orDie
    },
  )

  val layer: URLayer[SharedVolumes.Images, DspIngestTestContainer] =
    ZLayer.scoped(ZIO.service[SharedVolumes.Images].flatMap(make(_).toZio)) >+> initDspIngest
}
