package com.hippo.ehviewer.web.service

/**
 * Thrown when an operation requires a valid E-Hentai session but the shared login
 * cookies have expired. Mapped to HTTP 401 by [com.hippo.ehviewer.web.config.AuthExceptionHandler]
 * with a message prompting the user to log in again.
 */
class EhSessionExpiredException(
    message: String = "E-Hentai session has expired. Please log in again.",
) : RuntimeException(message)
