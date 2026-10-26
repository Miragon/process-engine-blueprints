package io.miragon.blueprint.domain.leasing;

public record CustomerName(String value) {

    public CustomerName {
        // Blank = only whitespace or space separators, so no-break spaces (U+00A0, U+2007, U+202F) count too.
        if (value.chars().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new IllegalArgumentException("Customer name must not be blank");
        }
    }
}
