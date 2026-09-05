package com.urbanfurniture.accounting.identity;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcdef", "user01", "a.b_c-d", "twelvechars1"})
    @DisplayName("accepts login IDs of 6 to 12 permitted characters")
    void acceptsValidLoginIds(String loginId) {
        assertThatCode(() -> CredentialPolicy.validateLoginId(loginId)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcde",           // 5 - one short
            "thirteenchars",   // 13 - one long
            "has space",       // space
            "has@symbol",      // disallowed punctuation
            ""                 // empty
    })
    @DisplayName("rejects login IDs outside the rules")
    void rejectsInvalidLoginIds(String loginId) {
        assertThatThrownBy(() -> CredentialPolicy.validateLoginId(loginId))
                .isInstanceOf(ApiExceptions.BusinessRuleException.class)
                .hasMessageContaining("6-12 characters");
    }

    @Test
    void rejectsNullLoginId() {
        assertThatThrownBy(() -> CredentialPolicy.validateLoginId(null))
                .isInstanceOf(ApiExceptions.BusinessRuleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Passw0rd!", "aB!45678", "Str0ng#Pass", "Xy!zabcd"})
    @DisplayName("accepts passwords with lower, upper, special and 8+ characters")
    void acceptsValidPasswords(String password) {
        assertThatCode(() -> CredentialPolicy.validatePassword(password)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a password shorter than eight characters")
    void rejectsShortPassword() {
        assertThatThrownBy(() -> CredentialPolicy.validatePassword("aB!4567"))
                .isInstanceOf(ApiExceptions.BusinessRuleException.class)
                .hasMessageContaining("at least 8 characters");
    }

    @Test
    void rejectsPasswordWithoutLowercase() {
        assertThatThrownBy(() -> CredentialPolicy.validatePassword("PASSW0RD!"))
                .isInstanceOf(ApiExceptions.BusinessRuleException.class)
                .hasMessageContaining("lowercase");
    }

    @Test
    void rejectsPasswordWithoutUppercase() {
        assertThatThrownBy(() -> CredentialPolicy.validatePassword("passw0rd!"))
                .isInstanceOf(ApiExceptions.BusinessRuleException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    void rejectsPasswordWithoutSpecialCharacter() {
        assertThatThrownBy(() -> CredentialPolicy.validatePassword("Passw0rd1"))
                .isInstanceOf(ApiExceptions.BusinessRuleException.class)
                .hasMessageContaining("special character");
    }

    @Test
    @DisplayName("rejects a common password even when it satisfies every rule")
    void rejectsCommonPassword() {
        // This is what replaces the unimplementable "passwords must be
        // unique" requirement: it delivers the intended protection without
        // comparing against every stored hash, and without disclosing that
        // another account uses the same password.
        assertThatThrownBy(() -> CredentialPolicy.validatePassword("P@ssw0rd"))
                .isInstanceOf(ApiExceptions.BusinessRuleException.class)
                .hasMessageContaining("too common");
    }

    @Test
    @DisplayName("login IDs and emails are compared case-insensitively")
    void normalisesForUniquenessChecks() {
        assertThatCode(() -> {
            assert "sam.seller".equals(CredentialPolicy.normalizeLoginId("  Sam.Seller "));
            assert "a@b.com".equals(CredentialPolicy.normalizeEmail(" A@B.COM "));
        }).doesNotThrowAnyException();
    }
}
