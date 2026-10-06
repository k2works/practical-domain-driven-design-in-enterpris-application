package com.example.cargotracker.identity.infrastructure.security;

import java.time.Duration;

/**
 * session の期限（US-18 AC1・AC5、SEC-03、IA-INV-03）。無操作の期限は Spring Session の設定、発行からの上限は
 * {@link SessionLifetimeFilter} で判定する。
 */
public final class SessionLifetime {

    /** 発行からの上限。 */
    public static final Duration MAX_LIFETIME = Duration.ofHours(8);

    /**
     * ログインの時刻を置く session の属性の名前。クラスの移動や改名で既存の session の属性が読めなくならないよう、固定の値にする。
     */
    public static final String AUTHENTICATED_AT = "cargotracker.session.authenticatedAt";

    /** 失効した session の request を移す先（A-03 再認証の案内）。 */
    public static final String EXPIRED_URL = "/session-expired";

    private SessionLifetime() {}
}
