package io.miragon.blueprint.domain.leasing;

/**
 * Lifecycle of a leasing application, mirrored from the process:
 * {@code RECEIVED} on submission, {@code ORDERED} once a bike order exists, {@code HANDED_OVER} once the bike is
 * handed to the customer (waiting-period token), {@code ACTIVE} when the leasing is live after the
 * withdrawal period elapses. {@code WITHDRAWN} is the in-progress cancellation state: the customer has
 * withdrawn and the compensation runs asynchronously (it may park on a return-clarification task)
 * before it reaches the terminal {@code CANCELLED}. {@code REJECTED} and {@code CANCELLED} are the terminal negative
 * outcomes.
 */
public enum LeasingStatus {
    RECEIVED,
    ORDERED,
    HANDED_OVER,
    ACTIVE,
    WITHDRAWN,
    REJECTED,
    CANCELLED,
}
