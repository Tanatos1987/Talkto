package com.talkto.core.error

/**
 * Every failure that can reach the user is expressed as a [TalktoError].
 * The UI never shows raw exception text: it maps [kind] to a friendly line the avatar speaks,
 * while [message] keeps the technical detail for logs and for the tool_result sent to Claude.
 */
sealed class TalktoError(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        PERMISSION_DENIED,
        PROTECTED_PATH,
        NOT_FOUND,
        ALREADY_EXISTS,
        INVALID_INPUT,
        CONFIRMATION_REQUIRED,
        NETWORK,
        RATE_LIMITED,
        API_KEY_MISSING,
        API_REJECTED,
        CAPABILITY_UNAVAILABLE,
        STORAGE_FULL,
        UNKNOWN,
    }

    class PermissionDenied(path: String, cause: Throwable? = null) :
        TalktoError(Kind.PERMISSION_DENIED, "No permission for: $path", cause)

    class ProtectedPath(path: String, reason: String) :
        TalktoError(Kind.PROTECTED_PATH, "Protected path '$path': $reason")

    class NotFound(what: String) :
        TalktoError(Kind.NOT_FOUND, "Not found: $what")

    class AlreadyExists(path: String) :
        TalktoError(Kind.ALREADY_EXISTS, "Already exists: $path")

    class InvalidInput(detail: String) :
        TalktoError(Kind.INVALID_INPUT, detail)

    class ConfirmationRequired(detail: String) :
        TalktoError(Kind.CONFIRMATION_REQUIRED, detail)

    class Network(detail: String, cause: Throwable? = null) :
        TalktoError(Kind.NETWORK, detail, cause)

    class RateLimited(detail: String, cause: Throwable? = null) :
        TalktoError(Kind.RATE_LIMITED, detail, cause)

    class ApiKeyMissing(service: String) :
        TalktoError(Kind.API_KEY_MISSING, "API key missing for $service")

    class ApiRejected(detail: String, cause: Throwable? = null) :
        TalktoError(Kind.API_REJECTED, detail, cause)

    class CapabilityUnavailable(detail: String) :
        TalktoError(Kind.CAPABILITY_UNAVAILABLE, detail)

    class StorageFull(detail: String, cause: Throwable? = null) :
        TalktoError(Kind.STORAGE_FULL, detail, cause)

    class Unknown(detail: String, cause: Throwable? = null) :
        TalktoError(Kind.UNKNOWN, detail, cause)
}
