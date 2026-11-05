package io.miragon.blueprint.domain.bike;

public record OrderId(String value) {

    public OrderId {
        if (value == null || value.chars().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new IllegalArgumentException("OrderId must not be blank");
        }
    }
}
