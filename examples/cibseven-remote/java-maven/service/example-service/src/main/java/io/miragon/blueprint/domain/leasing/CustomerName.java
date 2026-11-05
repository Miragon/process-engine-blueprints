package io.miragon.blueprint.domain.leasing;

public record CustomerName(String value) {

    public CustomerName {
        if (value == null || value.chars().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new IllegalArgumentException("Customer name must not be blank");
        }
    }
}
