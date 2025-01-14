/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.User

/**
 * This object represents built-in User and Project instances.
 */
object KnoraSystemInstances {

  object Users {

    /**
     * Represents the anonymous user.
     */
    val AnonymousUser = User(
      id = OntologyConstants.KnoraAdmin.AnonymousUser,
      username = "anonymous",
      email = "anonymous@localhost",
      givenName = "Knora",
      familyName = "Anonymous",
      status = true,
      lang = "en",
      password = None,
      groups = Seq.empty[Group],
      projects = Seq.empty[Project],
      permissions = PermissionsDataADM(),
    )

    /**
     * Represents the system user used internally.
     */
    val SystemUser = User(
      id = OntologyConstants.KnoraAdmin.SystemUser,
      username = "system",
      email = "system@localhost",
      givenName = "Knora",
      familyName = "System",
      status = true,
      lang = "en",
      password = None,
      groups = Seq.empty[Group],
      projects = Seq.empty[Project],
      permissions = PermissionsDataADM(),
    )
  }
}
