package io.miragon.blueprint.domain.leasing

/**
 * Lifecycle of a leasing application, mirrored from the process:
 * [RECEIVED] on submission, [ORDERED] once a bike order exists, [HANDED_OVER] once the bike is
 * handed to the customer (waiting-period token), [ACTIVE] when the leasing is live after the
 * withdrawal period elapses, and the two terminal negative outcomes [REJECTED] and [CANCELLED].
 */
enum class LeasingStatus {
    RECEIVED,
    ORDERED,
    HANDED_OVER,
    ACTIVE,
    REJECTED,
    CANCELLED,
}
