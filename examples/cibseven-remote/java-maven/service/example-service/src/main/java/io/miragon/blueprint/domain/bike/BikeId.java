package io.miragon.blueprint.domain.bike;

/** Identifies the concrete bike a leasing application is about — carried through to the order. */
public record BikeId(String value) {

    public BikeId {
        // Blank = only whitespace or space separators, so no-break spaces (U+00A0, U+2007, U+202F) count too.
        if (value.chars().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new IllegalArgumentException("BikeId must not be blank");
        }
    }
}
