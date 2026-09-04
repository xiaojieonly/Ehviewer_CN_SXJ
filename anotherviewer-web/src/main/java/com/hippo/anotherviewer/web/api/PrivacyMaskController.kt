package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.service.ServerConfigService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 内容打码模式的服务端状态（管理面板-高级页读写）。
 *
 * 这就是「内容打码模式」这唯一一个开关的服务端权威状态——开启时
 * [PrivacyMaskFilter] 对内容类 JSON 响应统一脱敏，Agent 等无头客户端
 * 同样只能拿到脱敏数据；不另设任何独立的"脱敏开关"。
 */
@RestController
@RequestMapping("/api/v1/privacy")
class PrivacyMaskController(private val serverConfig: ServerConfigService) {

    @GetMapping("/mask")
    fun getMask(): Map<String, Boolean> =
        mapOf("enabled" to serverConfig.getBoolean(ServerConfigService.KEY_PRIVACY_MASK))

    @PostMapping("/mask")
    fun setMask(@RequestBody body: PrivacyMaskRequest): Map<String, Boolean> {
        serverConfig.setBoolean(ServerConfigService.KEY_PRIVACY_MASK, body.enabled)
        return mapOf("enabled" to body.enabled)
    }
}

/** POST /privacy/mask 请求体。 */
data class PrivacyMaskRequest(val enabled: Boolean = false)
