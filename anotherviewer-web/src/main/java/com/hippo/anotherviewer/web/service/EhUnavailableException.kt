package com.hippo.anotherviewer.web.service

/**
 * Thrown by [GalleryLookupService] entry points (detail / page count / image URL
 * resolution) when [EhAvailabilityService.isBlocked] is true — i.e. the EH
 * upstream is currently judged unavailable and every automatic upstream access
 * must short-circuit without network I/O. Callers (GalleryService,
 * ImageProxyController, PrefetchService, DownloadService) already catch
 * [Exception] and convert it into their failure envelope (404 / success=false),
 * so this exception never escapes to a request handler.
 */
class EhUnavailableException(
    message: String = MESSAGE,
) : RuntimeException(message) {

    companion object {
        /** Error code used in the 404 error envelope (`error.code`). */
        const val CODE = "EH_UNAVAILABLE"

        /** Generic message; safe for log contexts. */
        const val MESSAGE = "EH 平台当前不可达"

        /** User-facing message carried by the 404 envelope. */
        const val USER_MESSAGE = "EH 平台当前不可达，仅显示本地内容"
    }
}
