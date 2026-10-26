package io.miragon.blueprint.domain.leasing;

/** Reference to the leasing contract issued by the (external) contract system. */
public record ContractId(String value) {

    public ContractId {
        // Blank = only whitespace or space separators, so no-break spaces (U+00A0, U+2007, U+202F) count too.
        if (value.chars().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new IllegalArgumentException("ContractId must not be blank");
        }
    }
}
