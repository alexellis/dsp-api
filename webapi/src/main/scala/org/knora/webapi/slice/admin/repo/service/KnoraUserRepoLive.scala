/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary._
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.{`var` => variable}
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.stream.ZStream

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions._
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive.UserQueries
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException

final case class KnoraUserRepoLive(triplestore: TriplestoreService, cacheService: CacheService) extends KnoraUserRepo {

  override def findById(id: UserIri): Task[Option[KnoraUser]] = {
    val construct = UserQueries.findById(id)
    for {
      model    <- triplestore.queryRdfModel(construct)
      resource <- model.getResource(id.value)
      user     <- ZIO.foreach(resource)(toUser)
    } yield user
  }
  override def findByEmail(email: Email): Task[Option[KnoraUser]] =
    findOneByQuery(UserQueries.findByEmail(email))

  override def findByUsername(username: Username): Task[Option[KnoraUser]] =
    findOneByQuery(UserQueries.findByUsername(username))

  private def findOneByQuery(construct: Construct) =
    for {
      model <- triplestore.queryRdfModel(construct)
      resource <- model
                    .getResourcesRdfType(KnoraAdmin.User)
                    .orElseFail(TriplestoreResponseException("Error while querying the triplestore"))
      user <- ZIO.foreach(resource.nextOption())(toUser)
    } yield user

  private def toUser(resource: RdfResource) = {
    for {
      userIri <-
        resource.iri.flatMap(it => ZIO.fromEither(UserIri.from(it.value))).mapError(TriplestoreResponseException.apply)
      username                  <- resource.getStringLiteralOrFail[Username](KnoraAdmin.Username)
      email                     <- resource.getStringLiteralOrFail[Email](KnoraAdmin.Email)
      familyName                <- resource.getStringLiteralOrFail[FamilyName](KnoraAdmin.FamilyName)
      givenName                 <- resource.getStringLiteralOrFail[GivenName](KnoraAdmin.GivenName)
      passwordHash              <- resource.getStringLiteralOrFail[PasswordHash](KnoraAdmin.Password)
      preferredLanguage         <- resource.getStringLiteralOrFail[LanguageCode](KnoraAdmin.PreferredLanguage)
      status                    <- resource.getBooleanLiteralOrFail[UserStatus](KnoraAdmin.StatusProp)
      isInProjectIris           <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.IsInProject)
      isInGroupIris             <- resource.getObjectIrisConvert[GroupIri](KnoraAdmin.IsInGroup)
      isInSystemAdminGroup      <- resource.getBooleanLiteralOrFail[SystemAdmin](KnoraAdmin.IsInSystemAdminGroup)
      isInProjectAdminGroupIris <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.IsInProjectAdminGroup)
    } yield KnoraUser(
      userIri,
      username,
      email,
      familyName,
      givenName,
      passwordHash,
      preferredLanguage,
      status,
      isInProjectIris,
      isInGroupIris,
      isInSystemAdminGroup,
      isInProjectAdminGroupIris,
    )
  }.mapError(e => TriplestoreResponseException(e.toString))

  /**
   * Returns all instances of the type.
   *
   * @return all instances of the type.
   */
  override def findAll(): Task[List[KnoraUser]] =
    for {
      model     <- triplestore.queryRdfModel(UserQueries.findAll)
      resources <- model.getResourcesRdfType(KnoraAdmin.User).option.map(_.getOrElse(Iterator.empty))
      users     <- ZStream.fromIterator(resources).mapZIO(toUser).runCollect
    } yield users.toList

  override def findByProjectMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
    for {
      model     <- triplestore.queryRdfModel(UserQueries.findProjectMembers(projectIri))
      resources <- model.getResourcesRdfType(KnoraAdmin.User).option.map(_.getOrElse(Iterator.empty))
      users     <- ZStream.fromIterator(resources).mapZIO(toUser).runCollect
    } yield users

  override def findByProjectAdminMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
    for {
      model     <- triplestore.queryRdfModel(UserQueries.findProjectAdminMembers(projectIri))
      resources <- model.getResourcesRdfType(KnoraAdmin.User).option.map(_.getOrElse(Iterator.empty))
      users     <- ZStream.fromIterator(resources).mapZIO(toUser).runCollect
    } yield users

  override def save(user: KnoraUser): Task[KnoraUser] =
    cacheService.invalidateUser(user.id) *> triplestore.query(UserQueries.save(user)).as(user)
}

object KnoraUserRepoLive {
  private object UserQueries {

    def findAll: Construct = {
      val (s, p, o) = (variable("s"), variable("p"), variable("o"))
      val query = Queries
        .CONSTRUCT(tp(s, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          s
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .and(s.has(p, o))
            .from(Vocabulary.NamedGraphs.knoraAdminIri),
        )
      Construct(query.getQueryString)
    }
    def findProjectMembers(projectIri: ProjectIri): Construct = {
      val (userIri, p, o) = (variable("s"), variable("p"), variable("o"))
      val query = Queries
        .CONSTRUCT(tp(userIri, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          userIri
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .and(userIri.has(p, o).andHas(Vocabulary.KnoraAdmin.isInProject, Rdf.iri(projectIri.value)))
            .from(Vocabulary.NamedGraphs.knoraAdminIri),
        )
      Construct(query.getQueryString)
    }
    def findProjectAdminMembers(projectIri: ProjectIri): Construct = {
      val (userIri, p, o) = (variable("s"), variable("p"), variable("o"))
      val query = Queries
        .CONSTRUCT(tp(userIri, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          userIri
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .and(userIri.has(p, o).andHas(Vocabulary.KnoraAdmin.isInProjectAdminGroup, Rdf.iri(projectIri.value)))
            .from(Vocabulary.NamedGraphs.knoraAdminIri),
        )
      Construct(query.getQueryString)
    }

    def findById(id: UserIri): Construct = {
      val s      = Rdf.iri(id.value)
      val (p, o) = (variable("p"), variable("o"))
      val query: ConstructQuery = Queries
        .CONSTRUCT(tp(s, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          s
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .and(tp(s, p, o))
            .from(Vocabulary.NamedGraphs.knoraAdminIri),
        )
      Construct(query)
    }

    def findByEmail(email: Email): Construct =
      findByProperty(Vocabulary.KnoraAdmin.email, email.value)

    def findByUsername(username: Username): Construct =
      findByProperty(Vocabulary.KnoraAdmin.username, username.value)

    private def findByProperty(property: Iri, propertyValue: String) = {
      val (s, p, o) = (variable("s"), variable("p"), variable("o"))
      val query: ConstructQuery = Queries
        .CONSTRUCT(tp(s, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          s
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .andHas(property, Rdf.literalOf(propertyValue))
            .andHas(p, o)
            .from(Vocabulary.NamedGraphs.knoraAdminIri),
        )
      Construct(query)
    }

    private def deleteWhere(
      id: Iri,
      rdfType: Iri,
      query: ModifyQuery,
      iris: List[Iri],
    ): ModifyQuery =
      query
        .delete(iris.zipWithIndex.foldLeft(id.has(RDF.TYPE, rdfType)) { case (p, (iri, index)) =>
          p.andHas(iri, variable(s"n${index}"))
        })
        .where(iris.zipWithIndex.foldLeft(id.has(RDF.TYPE, rdfType).optional()) { case (p, (iri, index)) =>
          p.and(id.has(iri, variable(s"n${index}")).optional())
        })

    def save(u: KnoraUser): Update = {
      val query: ModifyQuery =
        Queries
          .MODIFY()
          .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS))
          .`with`(Rdf.iri(adminDataNamedGraph.value))
          .insert(toTriples(u))

      Update(deleteWhere(Rdf.iri(u.id.value), Vocabulary.KnoraAdmin.User, query, deletionFields))
    }

    private val deletionFields: List[Iri] = List(
      Vocabulary.KnoraAdmin.username,
      Vocabulary.KnoraAdmin.email,
      Vocabulary.KnoraAdmin.givenName,
      Vocabulary.KnoraAdmin.familyName,
      Vocabulary.KnoraAdmin.status,
      Vocabulary.KnoraAdmin.preferredLanguage,
      Vocabulary.KnoraAdmin.password,
      Vocabulary.KnoraAdmin.isInSystemAdminGroup,
      Vocabulary.KnoraAdmin.isInProject,
      Vocabulary.KnoraAdmin.isInGroup,
      Vocabulary.KnoraAdmin.isInProjectAdminGroup,
    )

    private def toTriples(u: KnoraUser) = {
      import Vocabulary.KnoraAdmin._
      Rdf
        .iri(u.id.value)
        .has(RDF.TYPE, User)
        .andHas(username, Rdf.literalOf(u.username.value))
        .andHas(email, Rdf.literalOf(u.email.value))
        .andHas(givenName, Rdf.literalOf(u.givenName.value))
        .andHas(familyName, Rdf.literalOf(u.familyName.value))
        .andHas(preferredLanguage, Rdf.literalOf(u.preferredLanguage.value))
        .andHas(status, Rdf.literalOf(u.status.value))
        .andHas(password, Rdf.literalOf(u.password.value))
        .andHas(isInSystemAdminGroup, Rdf.literalOf(u.isInSystemAdminGroup.value))
        .andHas(isInProject, u.isInProject.map(p => Rdf.iri(p.value)).toList: _*)
        .andHas(isInGroup, u.isInGroup.map(p => Rdf.iri(p.value)).toList: _*)
        .andHas(isInProjectAdminGroup, u.isInProjectAdminGroup.map(p => Rdf.iri(p.value)).toList: _*)
    }
  }
  val layer = ZLayer.derive[KnoraUserRepoLive]
}
