package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import javax.sql.DataSource

class HealthControllerTest {

    private lateinit var dataSource: DataSource
    private lateinit var config: SiteCoreConfigProperties
    private lateinit var controller: HealthController

    /** Mirrors the controller's own loader: read version.properties from the classpath. */
    private fun versionFromResource(): String {
        val stream = HealthControllerTest::class.java.getResourceAsStream("/version.properties")
            ?: return "1.0.0-SNAPSHOT"
        return stream.use {
            java.util.Properties().apply { load(it) }.getProperty("anotherviewer.version")
                ?: "1.0.0-SNAPSHOT"
        }
    }

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        dataSource = mock(DataSource::class.java)
        config = SiteCoreConfigProperties()
        config.download.cachePath = tempDir.absolutePath
        controller = HealthController(dataSource, config)
    }

    @Test
    fun `health returns UP when database is accessible and disk cache is writable`() {
        val connection = mock(Connection::class.java)
        val statement = mock(Statement::class.java)
        val resultSet = mock(ResultSet::class.java)

        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.createStatement()).thenReturn(statement)
        `when`(statement.executeQuery("SELECT 1")).thenReturn(resultSet)
        `when`(resultSet.next()).thenReturn(true)

        val response = controller.healthCheck()

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertTrue(body.status == "UP" || body.status == "DEGRADED")
        assertEquals("UP", body.components["database"]?.status)
        assertEquals("UP", body.components["diskCache"]?.status)
        assertNotNull(body.version)
        assertNotNull(body.uptime)
        assertTrue(body.uptimeMs >= 0)
        assertNotNull(body.timestamp)
    }

    @Test
    fun `health returns DOWN when database is inaccessible`() {
        `when`(dataSource.connection).thenThrow(RuntimeException("connection refused"))

        val response = controller.healthCheck()

        assertEquals(503, response.statusCode.value())
        val body = response.body!!
        assertEquals("DOWN", body.status)
        assertEquals("DOWN", body.components["database"]?.status)
    }

    @Test
    fun `health returns DOWN when disk cache has insufficient space`() {
        val connection = mock(Connection::class.java)
        val statement = mock(Statement::class.java)
        val resultSet = mock(ResultSet::class.java)

        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.createStatement()).thenReturn(statement)
        `when`(statement.executeQuery("SELECT 1")).thenReturn(resultSet)
        `when`(resultSet.next()).thenReturn(true)

        // Point to a non-writable path
        config.download.cachePath = "/nonexistent/path/that/cannot/be/created"

        val response = controller.healthCheck()
        val body = response.body!!

        // diskCache should be DOWN (can't create dir)
        assertEquals("DOWN", body.components["diskCache"]?.status)
    }

    @Test
    fun `health response includes all required components`() {
        val connection = mock(Connection::class.java)
        val statement = mock(Statement::class.java)
        val resultSet = mock(ResultSet::class.java)

        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.createStatement()).thenReturn(statement)
        `when`(statement.executeQuery("SELECT 1")).thenReturn(resultSet)
        `when`(resultSet.next()).thenReturn(true)

        val response = controller.healthCheck()
        val body = response.body!!

        assertTrue(body.components.containsKey("database"))
        assertTrue(body.components.containsKey("diskCache"))
        assertTrue(body.components.containsKey("galleryApi"))
    }

    @Test
    fun `health response includes version and uptime`() {
        val connection = mock(Connection::class.java)
        val statement = mock(Statement::class.java)
        val resultSet = mock(ResultSet::class.java)

        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.createStatement()).thenReturn(statement)
        `when`(statement.executeQuery("SELECT 1")).thenReturn(resultSet)
        `when`(resultSet.next()).thenReturn(true)

        val response = controller.healthCheck()
        val body = response.body!!

        assertEquals(versionFromResource(), body.version)
        assertTrue(body.uptime.isNotEmpty())
        assertTrue(body.uptimeMs > 0)
    }

    @Test
    fun `health component details values are strings`() {
        val connection = mock(Connection::class.java)
        val statement = mock(Statement::class.java)
        val resultSet = mock(ResultSet::class.java)

        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.createStatement()).thenReturn(statement)
        `when`(statement.executeQuery("SELECT 1")).thenReturn(resultSet)
        `when`(resultSet.next()).thenReturn(true)

        val response = controller.healthCheck()
        val body = response.body!!

        assertEquals(mapOf("type" to "sqlite"), body.components["database"]?.details)
        val diskDetails = body.components["diskCache"]?.details
        assertNotNull(diskDetails)
        assertTrue(diskDetails!!.values.all { it is String })
    }

    @Test
    fun `health component details normalize numeric values to strings`() {
        `when`(dataSource.connection).thenThrow(RuntimeException("connection refused"))

        val response = controller.healthCheck()
        val body = response.body!!

        assertEquals(mapOf("reason" to "connection refused"), body.components["database"]?.details)
    }
}