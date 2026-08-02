package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ServerConfigRepository : JpaRepository<ServerConfigEntity, String>
