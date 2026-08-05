package com.hippo.anotherviewer.web.entity

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files

/**
 * R2-F1: the W4 sync-pull indexes (@Table(indexes = ...) on the sync
 * entities) must ACTUALLY exist on the real JPA/Hibernate + SQLite path —
 * annotation presence alone does not prove Hibernate emits the CREATE INDEX
 * statements for the SQLite dialect. Boots the JPA slice against a throwaway
 * SQLite file (ddl-auto = create) and asserts via PRAGMA index_list.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SqliteIndexDdlTest {

    companion object {
        private val dbDir = Files.createTempDirectory("av-index-ddl")

        @JvmStatic
        @DynamicPropertySource
        fun sqliteProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:sqlite:${dbDir.resolve("index-ddl.db")}" }
            registry.add("spring.datasource.driver-class-name") { "org.sqlite.JDBC" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.community.dialect.SQLiteDialect" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
        }
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    /** PRAGMA index_list names; sqlite_autoindex_* entries filtered out. */
    private fun indexNames(table: String): List<String> =
        jdbc.queryForList("PRAGMA index_list($table)")
            .map { it["name"] as String }
            .filter { it.startsWith("idx_") }

    @Test
    fun `W4 index DDL is materialized on sqlite`() {
        // H-3/W4: the (username, last_modified) composite index that the
        // incremental-pull query (findByUsernameAndLastModifiedGreaterThan)
        // depends on must exist on the history table.
        val historyIndexes = indexNames("history_info")
        assertTrue(
            historyIndexes.contains("idx_history_username_lm"),
            "idx_history_username_lm missing on history_info, got: $historyIndexes",
        )

        // At least two other W4 sync-pull indexes must be materialized too.
        val favoriteIndexes = indexNames("local_favorite_info")
        assertTrue(
            favoriteIndexes.contains("idx_favorite_username_lm"),
            "idx_favorite_username_lm missing on local_favorite_info, got: $favoriteIndexes",
        )
        val bookmarkIndexes = indexNames("bookmark_info")
        assertTrue(
            bookmarkIndexes.contains("idx_bookmark_username_lm"),
            "idx_bookmark_username_lm missing on bookmark_info, got: $bookmarkIndexes",
        )

        // ADR-0004: ehSession 单例行依赖 (username, last_modified) 索引。
        val ehSessionIndexes = indexNames("eh_session")
        assertTrue(
            ehSessionIndexes.contains("idx_eh_session_username_lm"),
            "idx_eh_session_username_lm missing on eh_session, got: $ehSessionIndexes",
        )
    }
}
