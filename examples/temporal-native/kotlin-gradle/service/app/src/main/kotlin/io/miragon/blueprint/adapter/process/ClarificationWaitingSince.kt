package io.miragon.blueprint.adapter.process

import io.temporal.common.SearchAttributeKey
import java.time.OffsetDateTime

val CLARIFICATION_WAITING_SINCE: SearchAttributeKey<OffsetDateTime> =
    SearchAttributeKey.forOffsetDateTime("ClarificationWaitingSince")
