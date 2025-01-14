/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio._

import java.util.UUID

import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin._
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries._
import org.knora.webapi.util.ZioHelper

/**
 * Returns information about groups.
 */
trait GroupsResponderADM {

  /**
   * Gets all the groups (without built-in groups) and returns them as a sequence of [[Group]].
   *
   * @return all the groups as a sequence of [[Group]].
   */
  def groupsGetADM: Task[Seq[Group]]

  /**
   * Gets the group with the given group IRI and returns the information as a [[Group]].
   *
   * @param groupIri the IRI of the group requested.
   * @return information about the group as a [[Group]]
   */
  def groupGetADM(groupIri: IRI): Task[Option[Group]]

  /**
   * Gets the groups with the given IRIs and returns a set of [[GroupGetResponseADM]] objects.
   *
   * @param groupIris the IRIs of the groups being requested
   * @return information about the group as a set of [[GroupGetResponseADM]] objects.
   */
  def multipleGroupsGetRequestADM(groupIris: Set[IRI]): Task[Set[GroupGetResponseADM]]

  /**
   * Gets the group members with the given group IRI and returns the information as a [[GroupMembersGetResponseADM]].
   * Only project and system admins are allowed to access this information.
   *
   * @param iri       the IRI of the group.
   * @param user      the user initiating the request.
   * @return A [[GroupMembersGetResponseADM]]
   */
  def groupMembersGetRequest(iri: GroupIri, user: User): Task[GroupMembersGetResponseADM]

  /**
   * Create a new group.
   *
   * @param request  the create request information.
   * @param apiRequestID   the unique request ID.
   * @return a [[GroupGetResponseADM]]
   */
  def createGroup(
    request: GroupCreateRequest,
    apiRequestID: UUID,
  ): Task[GroupGetResponseADM]

  /**
   * Change group's basic information.
   *
   * @param groupIri      the IRI of the group we want to change.
   * @param request       the change request.
   * @param apiRequestID  the unique request ID.
   * @return a [[GroupGetResponseADM]].
   */
  def updateGroup(
    groupIri: GroupIri,
    request: GroupUpdateRequest,
    apiRequestID: UUID,
  ): Task[GroupGetResponseADM]

  /**
   * Change group's status.
   *
   * @param groupIri           the IRI of the group we want to change.
   * @param request            the change request.
   * @param apiRequestID       the unique request ID.
   * @return a [[GroupGetResponseADM]].
   */
  def updateGroupStatus(
    groupIri: GroupIri,
    request: GroupStatusUpdateRequest,
    apiRequestID: UUID,
  ): Task[GroupGetResponseADM]

  /**
   * Delete a group by changing its status to 'false'.
   *
   * @param iri                the IRI of the group to be deleted.
   * @param apiRequestID       the unique request ID.
   * @return a [[GroupGetResponseADM]].
   */
  def deleteGroup(
    iri: GroupIri,
    apiRequestID: UUID,
  ): Task[GroupGetResponseADM]
}

final case class GroupsResponderADMLive(
  triplestore: TriplestoreService,
  messageRelay: MessageRelay,
  iriService: IriService,
  knoraUserService: KnoraUserService,
  projectService: ProjectService,
  implicit val stringFormatter: StringFormatter,
) extends GroupsResponderADM
    with MessageHandler
    with GroupsADMJsonProtocol
    with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[GroupsResponderRequestADM]

  /**
   * Receives a message extending [[GroupsResponderRequestADM]], and returns an appropriate response message
   */
  def handle(msg: ResponderRequest): Task[Any] = msg match {
    case r: GroupGetADM                 => groupGetADM(r.groupIri)
    case r: MultipleGroupsGetRequestADM => multipleGroupsGetRequestADM(r.groupIris)
    case other                          => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets all the groups (without built-in groups) and returns them as a sequence of [[Group]].
   *
   * @return all the groups as a sequence of [[Group]].
   */
  override def groupsGetADM: Task[Seq[Group]] = {
    val query = Construct(sparql.admin.txt.getGroups(None))
    for {
      groupsResponse <- triplestore.query(query).flatMap(_.asExtended)
      groups          = groupsResponse.statements.map(convertStatementsToGroupADM)
      result         <- ZioHelper.sequence(groups.toSeq)
    } yield result.sorted
  }

  private def convertStatementsToGroupADM(statements: (SubjectV2, ConstructPredicateObjects)): Task[Group] = {
    val groupIri: SubjectV2                      = statements._1
    val propertiesMap: ConstructPredicateObjects = statements._2
    def getOption[A <: LiteralV2](key: IRI): UIO[Option[Seq[A]]] =
      ZIO.succeed(propertiesMap.get(key.toSmartIri).map(_.map(_.asInstanceOf[A])))
    def getOrFail[A <: LiteralV2](key: IRI): Task[Seq[A]] =
      getOption[A](key)
        .flatMap(ZIO.fromOption(_))
        .orElseFail(InconsistentRepositoryDataException(s"Project: $groupIri has no $key defined."))
    def getFirstValueOrFail[A <: LiteralV2](key: IRI): Task[A] = getOrFail[A](key).map(_.head)
    for {
      projectIri <- getFirstValueOrFail[IriLiteralV2](BelongsToProject).map(_.value)
      project <- findProjectByIriOrFail(
                   projectIri,
                   InconsistentRepositoryDataException(
                     s"Project $projectIri was referenced by $groupIri but was not found in the triplestore.",
                   ),
                 )
      name         <- getFirstValueOrFail[StringLiteralV2](GroupName).map(_.value)
      descriptions <- getOrFail[StringLiteralV2](GroupDescriptions)
      status       <- getFirstValueOrFail[BooleanLiteralV2](StatusProp).map(_.value)
      selfjoin     <- getFirstValueOrFail[BooleanLiteralV2](HasSelfJoinEnabled).map(_.value)
    } yield Group(groupIri.toString, name, descriptions, project, status, selfjoin)
  }

  private def findProjectByIriOrFail(iri: String, failReason: Throwable): Task[Project] =
    for {
      id      <- ZIO.fromEither(model.KnoraProject.ProjectIri.from(iri)).mapError(BadRequestException.apply)
      project <- projectService.findById(id).someOrFail(failReason)
    } yield project

  /**
   * Gets the group with the given group IRI and returns the information as a [[Group]].
   *
   * @param groupIri       the IRI of the group requested.
   * @return information about the group as a [[Group]]
   */
  override def groupGetADM(groupIri: IRI): Task[Option[Group]] = {
    val query = Construct(sparql.admin.txt.getGroups(maybeIri = Some(groupIri)))
    for {
      statements <- triplestore.query(query).flatMap(_.asExtended).map(_.statements.headOption)
      maybeGroup <- statements.map(convertStatementsToGroupADM).map(_.map(Some(_))).getOrElse(ZIO.succeed(None))
    } yield maybeGroup
  }

  /**
   * Gets the groups with the given IRIs and returns a set of [[GroupGetResponseADM]] objects.
   *
   * @param groupIris      the IRIs of the groups being requested
   * @return information about the group as a set of [[GroupGetResponseADM]] objects.
   */
  override def multipleGroupsGetRequestADM(groupIris: Set[IRI]): Task[Set[GroupGetResponseADM]] =
    ZioHelper.sequence(groupIris.map { iri =>
      groupGetADM(iri)
        .flatMap(ZIO.fromOption(_))
        .mapBoth(_ => NotFoundException(s"Group <$iri> not found."), GroupGetResponseADM.apply)
    })

  /**
   * Gets the members with the given group IRI and returns the information as a sequence of [[User]].
   *
   * @param groupIri             the IRI of the group.
   * @param requestingUser       the user initiating the request.
   * @return A sequence of [[User]]
   */
  private def groupMembersGetADM(
    groupIri: IRI,
    requestingUser: User,
  ): Task[Seq[User]] =
    for {
      group <- groupGetADM(groupIri)
                 .flatMap(ZIO.fromOption(_))
                 .orElseFail(NotFoundException(s"Group <$groupIri> not found"))

      // check if the requesting user is allowed to access the information
      _ <- ZIO
             .fail(ForbiddenException("Project members can only be retrieved by a project or system admin."))
             .when {
               val userPermissions = requestingUser.permissions
               !userPermissions.isProjectAdmin(group.project.id) &&
               !userPermissions.isSystemAdmin && !requestingUser.isSystemUser
             }

      groupMembersResponse <- triplestore.query(Select(sparql.admin.txt.getGroupMembersByIri(groupIri)))

      // get project member IRI from results rows
      groupMemberIris =
        if (groupMembersResponse.results.bindings.nonEmpty) {
          groupMembersResponse.results.bindings.map(_.rowMap("s"))
        } else {
          Seq.empty[IRI]
        }

      usersMaybeTasks: Seq[Task[Option[User]]] =
        groupMemberIris.map { userIri =>
          messageRelay
            .ask[Option[User]](
              UserGetByIriADM(
                UserIri.unsafeFrom(userIri),
                UserInformationType.Restricted,
                KnoraSystemInstances.Users.SystemUser,
              ),
            )
        }
      users <- ZioHelper.sequence(usersMaybeTasks).map(_.flatten)
    } yield users

  /**
   * Gets the group members with the given group IRI and returns the information as a [[GroupMembersGetResponseADM]].
   * Only project and system admins are allowed to access this information.
   *
   * @param iri             the IRI of the group.
   * @param user       the user initiating the request.
   * @return A [[GroupMembersGetResponseADM]]
   */
  override def groupMembersGetRequest(iri: GroupIri, user: User): Task[GroupMembersGetResponseADM] =
    groupMembersGetADM(iri.value, user).map(GroupMembersGetResponseADM.apply)

  /**
   * Create a new group.
   *
   * @param request              the create request information.
   * @param apiRequestID         the unique request ID.
   * @return a [[GroupGetResponseADM]]
   */
  override def createGroup(
    request: GroupCreateRequest,
    apiRequestID: UUID,
  ): Task[GroupGetResponseADM] = {
    val task = for {
      nameExists <- groupByNameAndProjectExists(request.name.value, request.project.value)
      _ <- ZIO
             .fail(DuplicateValueException(s"Group with the name '${request.name.value}' already exists"))
             .when(nameExists)

      project <-
        findProjectByIriOrFail(
          request.project.value,
          NotFoundException(s"Cannot create group inside project <${request.project}>. The project was not found."),
        )

      // check the custom IRI; if not given, create an unused IRI
      customGroupIri: Option[SmartIri] = request.id.map(_.value).map(iri => iri.toSmartIri)
      groupIri                        <- iriService.checkOrCreateEntityIri(customGroupIri, GroupIri.makeNew(project.getShortcode).value)

      /* create the group */
      createNewGroupSparqlString =
        sparql.admin.txt
          .createNewGroup(
            AdminConstants.adminDataNamedGraph.value,
            groupIri,
            groupClassIri = OntologyConstants.KnoraAdmin.UserGroup,
            name = request.name.value,
            descriptions = request.descriptions.value,
            projectIri = request.project.value,
            status = request.status.value,
            hasSelfJoinEnabled = request.selfjoin.value,
          )

      _ <- triplestore.query(Update(createNewGroupSparqlString))

      /* Verify that the group was created and updated  */
      createdGroup <-
        groupGetADM(groupIri)
          .someOrFail(UpdateNotPerformedException("Group was not created. Please report this as a possible bug."))

    } yield GroupGetResponseADM(createdGroup)
    IriLocker.runWithIriLock(apiRequestID, "http://rdfh.ch/groups", task)
  }

  /**
   * Change group's basic information.
   *
   * @param groupIri             the IRI of the group we want to change.
   * @param request              the change request.
   * @param apiRequestID         the unique request ID.
   * @return a [[GroupGetResponseADM]].
   */
  override def updateGroup(
    groupIri: GroupIri,
    request: GroupUpdateRequest,
    apiRequestID: UUID,
  ): Task[GroupGetResponseADM] = {
    val task = for {
      result <- updateGroupHelper(groupIri, request)
    } yield result
    IriLocker.runWithIriLock(apiRequestID, groupIri.value, task)
  }

  /**
   * Change group's status.
   *
   * @param groupIri             the IRI of the group we want to change.
   * @param request              the change request.
   * @param apiRequestID         the unique request ID.
   * @return a [[GroupGetResponseADM]].
   */
  override def updateGroupStatus(
    groupIri: GroupIri,
    request: GroupStatusUpdateRequest,
    apiRequestID: UUID,
  ): Task[GroupGetResponseADM] = {
    val task = for {
      // update group status
      updated <- updateGroupHelper(groupIri, GroupUpdateRequest(None, None, Some(request.status), None))

      // remove all members from group if status is false
      result <- removeGroupMembersIfNecessary(updated.group)
    } yield result
    IriLocker.runWithIriLock(apiRequestID, groupIri.value, task)
  }

  override def deleteGroup(
    iri: GroupIri,
    apiRequestID: UUID,
  ): Task[GroupGetResponseADM] = {
    val task = for {
      updated <- updateGroupHelper(iri, GroupUpdateRequest(None, None, Some(GroupStatus.inactive), None))
      result  <- removeGroupMembersIfNecessary(updated.group)
    } yield result
    IriLocker.runWithIriLock(apiRequestID, iri.value, task)
  }

  /**
   * Main group update method.
   *
   * @param groupIri  the IRI of the group we are updating.
   * @param request   the payload holding the information which we want to update.
   * @return a [[GroupGetResponseADM]]
   */
  private def updateGroupHelper(groupIri: GroupIri, request: GroupUpdateRequest) =
    for {
      _ <- ZIO
             .fail(BadRequestException("No data would be changed. Aborting update request."))
             .when(
               // parameter list is empty
               List(
                 request.name,
                 request.descriptions,
                 request.status,
                 request.selfjoin,
               ).flatten.isEmpty,
             )

      /* Verify that the group exists. */
      groupADM <-
        groupGetADM(groupIri.value)
          .someOrFail(NotFoundException(s"Group <${groupIri.value}> not found. Aborting update request."))

      /* Verify that the potentially new name is unique */
      groupByNameAlreadyExists <-
        if (request.name.nonEmpty)
          groupByNameAndProjectExists(request.name.get.value, groupADM.project.id)
        else ZIO.succeed(false)
      _ <- ZIO
             .fail(BadRequestException(s"Group with the name '${request.name.get.value}' already exists."))
             .when(groupByNameAlreadyExists)

      /* Update group */
      updateGroupSparqlString =
        sparql.admin.txt
          .updateGroup(
            adminNamedGraphIri = "http://www.knora.org/data/admin",
            groupIri.value,
            maybeName = request.name.map(_.value),
            maybeDescriptions = request.descriptions.map(_.value),
            maybeProject = None, // maybe later we want to allow moving of a group to another project
            maybeStatus = request.status.map(_.value),
            maybeSelfjoin = request.selfjoin.map(_.value),
          )
      _ <- triplestore.query(Update(updateGroupSparqlString))

      /* Verify that the project was updated. */
      updatedGroup <-
        groupGetADM(groupIri.value)
          .someOrFail(UpdateNotPerformedException("Group was not updated. Please report this as a possible bug."))
    } yield GroupGetResponseADM(updatedGroup)

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Helper method for checking if a group identified by name / project IRI exists.
   *
   * @param name       the name of the group.
   * @param projectIri the IRI of the project.
   * @return a [[Boolean]].
   */
  private def groupByNameAndProjectExists(name: String, projectIri: IRI): Task[Boolean] =
    triplestore.query(Ask(sparql.admin.txt.checkGroupExistsByName(projectIri, name)))

  /**
   * In the case that the group was deactivated (status = false), the
   * group members need to be removed from the group.
   *
   * @param changedGroup         the group with the new status.
   * @return a [[GroupGetResponseADM]]
   */
  private def removeGroupMembersIfNecessary(changedGroup: Group) =
    if (changedGroup.status) {
      // group active. no need to remove members.
      logger.debug("removeGroupMembersIfNecessary - group active. no need to remove members.")
      ZIO.succeed(GroupGetResponseADM(changedGroup))
    } else {
      // group deactivated. need to remove members.
      logger.debug("removeGroupMembersIfNecessary - group deactivated. need to remove members.")
      for {
        members <- groupMembersGetADM(changedGroup.id, KnoraSystemInstances.Users.SystemUser)
        _ <- ZIO.foreachDiscard(members)(user =>
               knoraUserService.removeUserFromGroup(user, changedGroup).mapError(BadRequestException.apply),
             )
      } yield GroupGetResponseADM(group = changedGroup)
    }
}

object GroupsResponderADMLive {
  val layer = ZLayer.fromZIO {
    for {
      ts      <- ZIO.service[TriplestoreService]
      iris    <- ZIO.service[IriService]
      sf      <- ZIO.service[StringFormatter]
      kus     <- ZIO.service[KnoraUserService]
      ps      <- ZIO.service[ProjectService]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(GroupsResponderADMLive(ts, mr, iris, kus, ps, sf))
    } yield handler
  }
}
