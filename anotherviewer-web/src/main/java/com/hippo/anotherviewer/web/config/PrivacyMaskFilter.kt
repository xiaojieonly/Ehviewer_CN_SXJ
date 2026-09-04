package com.hippo.anotherviewer.web.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper

/**
 * 隐私打码的服务端呼应（2026-09-04 用户裁决）：风控系统会读 API 端点的
 * 输出——只遮前端渲染不够，**打码开启时端点本身也必须返回脱敏数据**。
 *
 * 请求带 `X-Privacy-Mask: 1` 头（前端 axios 拦截器按打码开关附加）时，
 * 对作用域内端点的 JSON 响应做统一脱敏后再输出。Android App 不带此头，
 * 响应保持全量；配置类端点（/settings、/smb）不在作用域——path 等字段
 * 要在前端编辑框完整回显、保存回写，脱敏会毒化配置往返。
 *
 * 脱敏规则（JSON 树递归，见 [redact]）：
 * - 含 `gid` 的内容对象：title → `#gid`，titleJpn / uploader → ""，
 *   simpleTags / tags → []，galleryUrl → ""（站点地址含 token）；
 * - 评论对象（`comment` 文本字段）：comment → ""；
 * - 维护条目（`path` + `sizeBytes` 组合）：path → 前 10 字符。
 *
 * 历史回写（HistoryService.addHistory）对 `#<gid>` 形态的标题拒绝入库，
 * 防止脱敏响应经回写污染存量数据。
 */
@Component
class PrivacyMaskFilter(
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.getHeader(HEADER) != "1") return true
        val uri = request.requestURI
        return SCOPED_PREFIXES.none { uri.startsWith(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrapper = ContentCachingResponseWrapper(response)
        filterChain.doFilter(request, wrapper)

        val bytes = wrapper.contentAsByteArray
        val contentType = wrapper.contentType ?: ""
        val replaced: ByteArray? = if (bytes.isNotEmpty() && contentType.contains("application/json")) {
            // 解析失败（非预期 JSON 形态）→ null → 原样透传，绝不因脱敏吞掉响应。
            runCatching {
                val root = objectMapper.readTree(bytes)
                if (redact(root)) objectMapper.writeValueAsBytes(root) else null
            }.getOrNull()
        } else {
            null
        }

        if (replaced != null) {
            wrapper.resetBuffer()
            wrapper.setContentLength(replaced.size)
            wrapper.outputStream.write(replaced)
        }
        wrapper.copyBodyToResponse()
    }

    companion object {
        const val HEADER = "X-Privacy-Mask"

        /** 脱敏作用域：内容类 JSON 端点（图源/备份/配置等不在内）。 */
        private val SCOPED_PREFIXES = listOf(
            "/api/v1/gallery",
            "/api/v1/favorite",
            "/api/v1/history",
            "/api/v1/download",
            "/api/v1/search",
        )

        /** 打码序列号的形态（# + 纯数字）——历史回写据此拒绝污染标题。 */
        private val MASKED_TITLE = Regex("^#\\d{1,12}$")

        /** 判定一个标题是否为打码序列号（供 HistoryService 复用）。 */
        fun isMaskedTitle(title: String?): Boolean = title != null && MASKED_TITLE.matches(title)

        /**
         * 就地脱敏 JSON 树；返回是否有改动。规则按字段组合识别（锚点字段
         * 齐全才动手），避免误伤同名无关字段——如 Settings 的 download.path
         * （无 sizeBytes 孪生、也无 gid）不会命中任何规则。
         */
        fun redact(node: JsonNode): Boolean {
            var changed = false
            when (node) {
                is ObjectNode -> {
                    changed = redactObject(node)
                    // 先收集再递归：修改值不会增删字段，但先收集更稳。
                    val children = node.fields().asSequence().map { it.value }.toList()
                    for (child in children) {
                        if (child.isContainerNode && redact(child)) changed = true
                    }
                }
                is ArrayNode -> {
                    for (child in node) {
                        if (child.isContainerNode && redact(child)) changed = true
                    }
                }
            }
            return changed
        }

        private fun redactObject(obj: ObjectNode): Boolean {
            var changed = false

            // 内容对象锚点：gid + title → 标题/副标题/上传者/标签/站点地址一并脱敏
            val gid = obj["gid"]
            if (gid != null && gid.isNumber) {
                val title = obj["title"]
                if (title != null && title.isTextual && title.asText().isNotBlank()) {
                    obj.put("title", "#${gid.asLong()}")
                    changed = true
                }
                changed = putEmptyText(obj, "titleJpn") || changed
                changed = putEmptyText(obj, "uploader") || changed
                changed = putEmptyArray(obj, "simpleTags") || changed
                changed = putEmptyArray(obj, "tags") || changed
                changed = putEmptyText(obj, "galleryUrl") || changed
            }

            // 评论对象：comment 文本 + 上传者名（评论对象无 gid，靠字段组合识别）
            val comment = obj["comment"]
            if (comment != null && comment.isTextual && obj.has("time") && obj.has("score")) {
                obj.put("comment", "")
                changed = true
                changed = putEmptyText(obj, "uploader") || changed
            }

            // 维护条目：path + sizeBytes 组合 → 路径前 10 字符
            val path = obj["path"]
            if (path != null && path.isTextual && obj.has("sizeBytes")) {
                val p = path.asText()
                if (p.length > 10) {
                    obj.put("path", p.take(10))
                    changed = true
                }
            }

            return changed
        }

        private fun putEmptyText(obj: ObjectNode, field: String): Boolean {
            val v = obj[field] ?: return false
            if (v.isTextual && v.asText().isEmpty()) return false
            obj.put(field, "")
            return true
        }

        private fun putEmptyArray(obj: ObjectNode, field: String): Boolean {
            val v = obj[field] ?: return false
            if (v.isArray && v.size() == 0) return false
            obj.put(field, JsonNodeFactory.instance.arrayNode())
            return true
        }
    }
}
