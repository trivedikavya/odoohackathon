package com.urbanfurniture.accounting.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Application exception hierarchy. Each carries the HTTP status the API
 * should surface, so controllers never have to translate them by hand.
 */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** Base type for everything the API deliberately rejects. */
    public static class ApiException extends RuntimeException {
        private final HttpStatus status;

        public ApiException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    /** 404 - the requested record does not exist. */
    public static class NotFoundException extends ApiException {
        public NotFoundException(String entity, Object id) {
            super(HttpStatus.NOT_FOUND, entity + " not found: " + id);
        }

        public NotFoundException(String message) {
            super(HttpStatus.NOT_FOUND, message);
        }
    }

    /** 400 - the request violates a business rule (e.g. overpaying an invoice). */
    public static class BusinessRuleException extends ApiException {
        public BusinessRuleException(String message) {
            super(HttpStatus.BAD_REQUEST, message);
        }
    }

    /** 409 - the request conflicts with existing state (e.g. duplicate email). */
    public static class ConflictException extends ApiException {
        public ConflictException(String message) {
            super(HttpStatus.CONFLICT, message);
        }
    }

    /** 403 - authenticated, but not allowed to touch this particular row. */
    public static class ForbiddenException extends ApiException {
        public ForbiddenException(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    /**
     * 500 - a journal entry failed the double-entry invariant.
     * <p>
     * This is deliberately a server error, not a client error: an unbalanced
     * entry means the posting engine itself is wrong, and the transaction must
     * roll back rather than persist a corrupt ledger.
     */
    public static class UnbalancedEntryException extends ApiException {
        public UnbalancedEntryException(String message) {
            super(HttpStatus.INTERNAL_SERVER_ERROR, message);
        }
    }
}
