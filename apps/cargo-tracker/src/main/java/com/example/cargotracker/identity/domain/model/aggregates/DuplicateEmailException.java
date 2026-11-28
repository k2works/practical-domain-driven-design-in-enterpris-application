package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * 同じメールアドレスの利用者が既にある（大文字・小文字を区別しない。データモデル `app_user.email` の一意制約）。
 */
public class DuplicateEmailException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
