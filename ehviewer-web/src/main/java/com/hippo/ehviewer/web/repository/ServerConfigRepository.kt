package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.ServerConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ServerConfigRepository : JpaRepository<ServerConfigEntity, String>
