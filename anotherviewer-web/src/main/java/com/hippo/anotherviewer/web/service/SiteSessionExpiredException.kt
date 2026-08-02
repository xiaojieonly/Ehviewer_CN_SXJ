package com.hippo.anotherviewer.web.service

/**
 * Thrown when an operation requires a valid Gallery Site session but the shared login
 * cookies have expired. Mapped to HTTP 401 by [com.hippo.anotherviewer.web.config.AuthExceptionHandler]
 * with a message prompting the user to log in again.
 */
class SiteSessionExpiredException(
    message: String = "Gallery Site session has expired. Please log in again.",
) : RuntimeException(message)
