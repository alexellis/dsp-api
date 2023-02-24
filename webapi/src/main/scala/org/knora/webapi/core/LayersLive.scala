/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.GroupsResponderADMLive
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADMLive
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.admin.ProjectsResponderADMLive
import org.knora.webapi.responders.admin.ProjectsServiceLive
import org.knora.webapi.responders.admin.SipiResponderADM
import org.knora.webapi.responders.admin.SipiResponderADMLive
import org.knora.webapi.responders.admin.StoresResponderADM
import org.knora.webapi.responders.admin.StoresResponderADMLive
import org.knora.webapi.responders.admin.UsersResponderADM
import org.knora.webapi.responders.admin.UsersResponderADMLive
import org.knora.webapi.responders.v1.CkanResponderV1
import org.knora.webapi.responders.v1.CkanResponderV1Live
import org.knora.webapi.responders.v1.OntologyResponderV1
import org.knora.webapi.responders.v1.OntologyResponderV1Live
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.routing.admin.AuthenticatorService
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

object LayersLive {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type DspEnvironmentLive =
    ActorSystem
      with ApiRoutes
      with AppConfig
      with AppRouter
      with AppRouterRelayingMessageHandler
      with CacheServiceManager
      with CacheService
      with CkanResponderV1
      with GroupsResponderADM
      with HttpServer
      with IIIFServiceManager
      with IIIFService
      with IriService
      with JWTService
      with MessageRelay
      with OntologyResponderV1
      with PermissionsResponderADM
      with ProjectsResponderADM
      with RepositoryUpdater
      with RestResourceInfoService
      with RestCardinalityService
      with SipiResponderADM
      with State
      with StoresResponderADM
      with TriplestoreServiceManager
      with TriplestoreService
      with UsersResponderADM

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      ActorDeps.layer,
      ActorSystem.layer,
      ActorToZioBridge.live,
      ApiRoutes.layer,
      AppConfig.layer,
      AppRouter.layer,
      AppRouterRelayingMessageHandler.layer,
      AuthenticationMiddleware.layer,
      AuthenticatorService.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceManager.layer,
      CardinalityService.layer,
      CkanResponderV1Live.layer,
      GroupsResponderADMLive.layer,
      HttpServer.layer,
      HttpServerZ.layer, // this is the new ZIO HTTP server layer
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer,
      IriConverter.layer,
      IriService.layer,
      JWTService.layer,
      MessageRelayLive.layer,
      OntologyCache.layer,
      OntologyResponderV1Live.layer,
      OntologyRepoLive.layer,
      PermissionsResponderADMLive.layer,
      PredicateRepositoryLive.layer,
      ProjectsRouteZ.layer,
      ProjectsResponderADMLive.layer,
      ProjectsServiceLive.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      ResourceInfoRoute.layer,
      RestCardinalityService.layer,
      RestResourceInfoService.layer,
      SipiResponderADMLive.layer,
      State.layer,
      StoresResponderADMLive.layer,
      StringFormatter.live,
      TriplestoreServiceLive.layer,
      TriplestoreServiceManager.layer,
      UsersResponderADMLive.layer
    )
}
