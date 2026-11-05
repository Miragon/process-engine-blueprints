package io.miragon.blueprint.domain.leasing;

import java.util.UUID;

public record ApplicationId(UUID value) {

    public ApplicationId {
        if (value == null) {
            throw new IllegalArgumentException("ApplicationId must not be null");
        }
    }

    public static ApplicationId newId() {
        return new ApplicationId(UUID.randomUUID());
    }

    public static ApplicationId of(String value) {
        return new ApplicationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
