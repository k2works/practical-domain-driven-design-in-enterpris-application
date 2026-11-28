package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * その利用者の TOTP の資格が既にある（データモデル `totp_credential` の主キー。Bolt 31b）。同時の 2 つの登録のうち後の登録は、
 * 先の登録のコミットの後に主キーの違反でこの例外になり、自分のトランザクションをロールバックする（ADR-007 の「一意制約の違反は
 * 同じトランザクションで捕まえない」）。
 */
public class DuplicateTotpEnrollmentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateTotpEnrollmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
