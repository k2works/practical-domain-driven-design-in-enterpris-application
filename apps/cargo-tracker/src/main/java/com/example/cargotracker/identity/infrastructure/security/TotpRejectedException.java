package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.TotpVerification;
import java.io.Serial;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * TOTP の要素を拒んだ（Bolt 31）。password の誤りと同じ認証の失敗のイベントで監査記録に書き、Bolt 32 のロックでも数える
 * （ADR-011 の決定 2）。拒否の種類（一致しない・再利用）は監査記録の理由にだけ使い、利用者には同じ文言を示す（AC2）。
 */
public final class TotpRejectedException extends BadCredentialsException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final TotpVerification verification;

    public TotpRejectedException(TotpVerification verification) {
        super("認証コードが正しくない");
        this.verification = verification;
    }

    public TotpVerification verification() {
        return verification;
    }
}
