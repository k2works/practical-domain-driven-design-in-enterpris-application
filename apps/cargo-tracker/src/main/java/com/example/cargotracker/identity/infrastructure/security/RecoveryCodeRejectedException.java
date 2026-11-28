package com.example.cargotracker.identity.infrastructure.security;

import java.io.Serial;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * 回復コードを受け付けなかった（Bolt 31c）。形の違い・誤り・使用済み・認証コードとの同時の送信を区別しない（利用者には TOTP の
 * 誤りと同じ文言。AC2）。失敗のイベントの対応表に足し、監査記録の理由 {@code INVALID_RECOVERY_CODE} を書く。
 */
public final class RecoveryCodeRejectedException extends BadCredentialsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RecoveryCodeRejectedException() {
        super("回復コードが正しくない");
    }
}
