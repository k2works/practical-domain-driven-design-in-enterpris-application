package com.example.cargotracker.operations.interfaces.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * メンテナンス表示（ADR-023。W10-1 の Bolt 59。CP-4）。有効なとき、業務のポートのすべての要求に、session と認証より前で 503 と固定の
 * 案内と {@code Retry-After} を返し、後ろへ渡さない。管理者が通り抜ける口は作らない。ヘルスチェックの管理用のポートは別の文脈なので
 * 通る。既定は無効で、無効のときは部品を作らない。切り替えは環境変数 {@code CARGOTRACKER_MAINTENANCE_ENABLED}（運用タスク
 * {@code maintenance:on}・{@code maintenance:off}）。
 *
 * <p>止めるのは HTTP の要求だけで、定期処理とイベントの配信は止まらない（DB への書き込みは続きうる）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "cargotracker.maintenance", name = "enabled", havingValue = "true")
public class MaintenanceFilter extends OncePerRequestFilter {

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="ja">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>メンテナンス中 - cargo-tracker</title>
            </head>
            <body>
            <main>
            <h1>ただいまメンテナンス中です</h1>
            <p>ご不便をおかけします。時間をおいてから、もう一度お試しください。</p>
            <p>操作の途中だった内容は保存されていないことがあります。再開の後に確かめてください。</p>
            </main>
            </body>
            </html>
            """;

    private final long retryAfterSeconds;

    public MaintenanceFilter(@Value("${cargotracker.maintenance.retry-after-seconds:1800}") long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException {
        byte[] body = PAGE.getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
