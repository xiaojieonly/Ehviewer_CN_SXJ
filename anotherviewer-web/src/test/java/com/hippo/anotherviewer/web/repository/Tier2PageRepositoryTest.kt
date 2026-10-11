package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.Tier2PageEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files

/**
 * Tier-2 page cache persistence on the real JPA/Hibernate + SQLite stack
 * (temporary file, ddl-auto = create; mirrors JpaProcessingTaskStoreTest):
 * URL primary-key upsert keeps one row per page, TEXT bodies round trip,
 * the refresh query filters + orders by fetched_at, the fetched_at index is
 * actually materialized, and batch deletion works for capacity trimming.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class Tier2PageRepositoryTest {

    companion object {
        private val dbDir = Files.createTempDirectory("av-tier2-page")

        @JvmStatic
        @DynamicPropertySource
        fun sqliteProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:sqlite:${dbDir.resolve("tier2-page.db")}" }
            registry.add("spring.datasource.driver-class-name") { "org.sqlite.JDBC" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.community.dialect.SQLiteDialect" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
        }
    }

    @Autowired
    lateinit var repo: Tier2PageRepository

    @PersistenceContext
    lateinit var em: EntityManager

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun entity(url: String, body: String, fetchedAt: Long, contentType: String = "text/html; charset=UTF-8") =
        Tier2PageEntity().apply {
            this.url = url
            this.body = body
            this.contentType = contentType
            this.fetchedAt = fetchedAt
        }

    @Test
    fun `body and content type round trip through real sqlite`() {
        val body = "<html><title>画廊 &amp; 取阅</title>" + "x".repeat(500_000) + "</html>"
        repo.saveAndFlush(entity("https://e-hentai.org/g/1/abc/", body, fetchedAt = 100L))

        val loaded = repo.findById("https://e-hentai.org/g/1/abc/").orElseThrow()
        assertEquals(body, loaded.body)
        assertEquals("text/html; charset=UTF-8", loaded.contentType)
        assertEquals(100L, loaded.fetchedAt)
    }

    @Test
    fun `upsert by url primary key keeps one row`() {
        repo.saveAndFlush(entity("https://e-hentai.org/g/2/def/", "old", fetchedAt = 100L))
        repo.saveAndFlush(entity("https://e-hentai.org/g/2/def/", "new", fetchedAt = 200L))

        assertEquals(1, repo.count())
        val loaded = repo.findById("https://e-hentai.org/g/2/def/").orElseThrow()
        assertEquals("new", loaded.body)
        assertEquals(200L, loaded.fetchedAt)
    }

    @Test
    fun `refresh candidates are cutoff-filtered and oldest-first`() {
        repo.saveAll(
            listOf(
                entity("https://e-hentai.org/a", "a", fetchedAt = 300L),
                entity("https://e-hentai.org/b", "b", fetchedAt = 100L),
                entity("https://e-hentai.org/c", "c", fetchedAt = 200L),
                entity("https://e-hentai.org/fresh", "f", fetchedAt = 5_000L),
            )
        )
        repo.flush()

        val candidates = repo.findByFetchedAtLessThanOrderByFetchedAtAsc(
            1_000L,
            PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "fetchedAt")),
        )

        assertEquals(listOf("https://e-hentai.org/b", "https://e-hentai.org/c"), candidates.map { it.url })
    }

    @Test
    fun `fetched_at index is materialized on sqlite`() {
        val indexes = jdbc.queryForList("PRAGMA index_list(tier2_page)")
            .map { it["name"] as String }
            .filter { it.startsWith("idx_") }

        assertTrue(
            indexes.contains("idx_tier2_page_fetched_at"),
            "idx_tier2_page_fetched_at missing on tier2_page, got: $indexes",
        )
    }

    @Test
    fun `deleteAllInBatch removes the trim candidates`() {
        val a = entity("https://e-hentai.org/a", "a", fetchedAt = 1L)
        val b = entity("https://e-hentai.org/b", "b", fetchedAt = 2L)
        val c = entity("https://e-hentai.org/c", "c", fetchedAt = 3L)
        repo.saveAll(listOf(a, b, c))
        repo.flush()

        val oldest = repo.findAll(PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "fetchedAt"))).content
        repo.deleteAllInBatch(oldest)

        assertEquals(1, repo.count())
        assertTrue(repo.findById("https://e-hentai.org/c").isPresent)
    }
}
