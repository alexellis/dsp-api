package org.knora.webapi.core

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.GroupsResponderADMLive
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.admin.ProjectsResponderADMLive
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.IIIFServiceMockImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.TestClientService
import zio._

import org.knora.webapi.responders.admin.UsersResponderADM
import org.knora.webapi.responders.admin.UsersResponderADMLive
import org.knora.webapi.responders.admin.SipiResponderADM
import org.knora.webapi.responders.admin.SipiResponderADMLive
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADMLive
import org.knora.webapi.responders.v1.OntologyResponderV1
import org.knora.webapi.responders.v1.OntologyResponderV1Live
import org.knora.webapi.responders.v1.CkanResponderV1
import org.knora.webapi.responders.v1.CkanResponderV1Live
import org.knora.webapi.responders.admin.StoresResponderADM
import org.knora.webapi.responders.admin.StoresResponderADMLive

object LayersTest {

  /**
   * The `Environment`s that we require for the tests to run - with or without Sipi
   */
  type DefaultTestEnvironmentWithoutSipi = LayersLive.DspEnvironmentLive with FusekiTestContainer with TestClientService
  type DefaultTestEnvironmentWithSipi    = DefaultTestEnvironmentWithoutSipi with SipiTestContainer

  type CommonR0 = ActorSystem with IIIFService with AppConfig
  type CommonR = ActorDeps
    with ActorToZioBridge
    with ApiRoutes
    with AppRouter
    with AppRouterRelayingMessageHandler
    with CacheService
    with CacheServiceManager
    with CardinalityService
    with CkanResponderV1
    with GroupsResponderADM
    with HttpServer
    with IIIFServiceManager
    with IriConverter
    with IriService
    with MessageRelay
    with OntologyResponderV1
    with PermissionsResponderADM
    with ProjectsResponderADM
    with RepositoryUpdater
    with ResourceInfoRepo
    with RestCardinalityService
    with RestResourceInfoService
    with SipiResponderADM
    with State
    with StoresResponderADM
    with StringFormatter
    with TestClientService
    with TriplestoreService
    with TriplestoreServiceManager
    with UsersResponderADM

  private val commonLayersForAllIntegrationTests =
    ZLayer.makeSome[CommonR0, CommonR](
      ActorDeps.layer,
      ActorToZioBridge.live,
      ApiRoutes.layer,
      AppRouter.layer,
      AppRouterRelayingMessageHandler.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceManager.layer,
      CardinalityService.layer,
      CkanResponderV1Live.layer,
      GroupsResponderADMLive.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IriConverter.layer,
      IriService.layer,
      MessageRelayLive.layer,
      OntologyCache.layer,
      OntologyResponderV1Live.layer,
      OntologyRepoLive.layer,
      PermissionsResponderADMLive.layer,
      ProjectsResponderADMLive.layer,
      PredicateRepositoryLive.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      RestCardinalityService.layer,
      RestResourceInfoService.layer,
      SipiResponderADMLive.layer,
      State.layer,
      StoresResponderADMLive.layer,
      StringFormatter.test,
      TestClientService.layer,
      TriplestoreServiceLive.layer,
      TriplestoreServiceManager.layer,
      UsersResponderADMLive.layer
    )

  private val fusekiAndSipiTestcontainers =
    ZLayer.make[FusekiTestContainer with SipiTestContainer with AppConfig with JWTService with IIIFService](
      AppConfigForTestContainers.testcontainers,
      FusekiTestContainer.layer,
      SipiTestContainer.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer
    )

  private val fusekiTestcontainers =
    ZLayer.make[FusekiTestContainer with AppConfig with JWTService with IIIFService](
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      IIIFServiceMockImpl.layer,
      JWTService.layer
    )

  /**
   * Provides a layer for integration tests which depend on Fuseki as Testcontainers.
   * Sipi/IIIFService will be mocked with the [[IIIFServiceMockImpl]]
   * @param system An optional [[akka.actor.ActorSystem]] for use with Akka's [[akka.testkit.TestKit]]
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithoutSipi]]
   */
  def integrationTestsWithFusekiTestcontainers(
    system: Option[akka.actor.ActorSystem] = None
  ): ULayer[DefaultTestEnvironmentWithoutSipi] =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      commonLayersForAllIntegrationTests,
      fusekiTestcontainers,
      system.map(ActorSystemTest.layer).getOrElse(ActorSystem.layer)
    )

  /**
   * Provides a layer for integration tests which depend on Fuseki and Sipi as Testcontainers.
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithSipi]]
   */
  val integrationTestsWithSipiAndFusekiTestcontainers: ULayer[DefaultTestEnvironmentWithSipi] =
    ZLayer.make[DefaultTestEnvironmentWithSipi](
      commonLayersForAllIntegrationTests,
      fusekiAndSipiTestcontainers,
      ActorSystem.layer
    )
}
