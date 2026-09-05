package com.urbanfurniture.accounting.identity;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Credential rules, enforced server-side.
 * <p>
 * <b>On password uniqueness.</b> The original brief asked that passwords
 * be unique across users. That cannot be implemented safely: bcrypt
 * salts every hash, so checking uniqueness would mean re-hashing the
 * candidate against every stored row — seconds of CPU per signup — and a
 * "that password is taken" response tells an attacker that some other
 * account uses it. The protection actually intended is a ban on
 * easily-guessed passwords, which is what {@link #COMMON_PASSWORDS}
 * provides, at no cost and with no disclosure.
 */
public final class CredentialPolicy {

    /** Letters, digits and a few separators; 6-12 characters. */
    private static final Pattern LOGIN_ID = Pattern.compile("^[A-Za-z0-9._-]{6,12}$");

    private static final Pattern HAS_LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern HAS_UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    private static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * A deliberately small starter list. In production this would be the
     * top ~10k leaked passwords, or a k-anonymity lookup against a
     * breach corpus.
     */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password123", "passw0rd", "p@ssw0rd", "p@ssword",
            "qwerty123", "admin123", "welcome1", "letmein1", "iloveyou1", "abc12345",
            "12345678", "123456789", "qwertyuiop", "changeme", "trustno1", "monkey12");

    private CredentialPolicy() {
    }

    public static void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Login ID must be 6-12 characters and may contain only letters, digits, dot, underscore or hyphen");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiExceptions.BusinessRuleException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (!HAS_LOWER.matcher(password).matches()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Password must contain at least one lowercase letter");
        }
        if (!HAS_UPPER.matcher(password).matches()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Password must contain at least one uppercase letter");
        }
        if (!HAS_SPECIAL.matcher(password).matches()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Password must contain at least one special character");
        }
        if (COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new ApiExceptions.BusinessRuleException(
                    "That password is too common. Choose something less guessable");
        }
    }

    /** Normalised form used for uniqueness checks and lookup. */
    public static String normalizeLoginId(String loginId) {
        return loginId == null ? null : loginId.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
