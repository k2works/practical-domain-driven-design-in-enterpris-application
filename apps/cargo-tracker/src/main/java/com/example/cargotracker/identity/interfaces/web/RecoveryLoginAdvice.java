package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.domain.model.rules.SessionPolicy;
import com.example.cargotracker.platform.web.DurationDisplay;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 回復コードでログインした印の知らせ（Bolt 33b、UI 設計「共通部品」の「回復コードでログインした印の知らせ」）。印がある間、ログインした
 * 画面のレイアウトが上部に知らせを出す。15 分の間は「認証アプリを登録し直してください」と A-08 へのリンク、過ぎたらリンクのない
 * 「ログインし直してください」の案内。A-08 は本文が同じことを言うので、画面が {@code null} で上書きする。
 */
@ControllerAdvice
public class RecoveryLoginAdvice {

    /** レイアウトが読むモデルの属性の名前。値は {@code active}・{@code expired}・{@code null}（出さない）。 */
    static final String NOTICE = "recoveryLoginNotice";

    private final ObjectProvider<Clock> clock;

    /** 時計は合成ルートの {@code Clock} を使い、ない画面の単体テストのコンテキスト（{@code @WebMvcTest}）ではシステムの時計にする。 */
    public RecoveryLoginAdvice(ObjectProvider<Clock> clock) {
        this.clock = clock;
    }

    @ModelAttribute(NOTICE)
    public String recoveryLoginNotice(HttpServletRequest request, Optional<AuthenticatedActor> actor) {
        RecoveryLogin.State state = RecoveryLogin.stateOf(
                request.getSession(false),
                actor.orElse(null),
                clock.getIfAvailable(Clock::systemUTC).instant());
        return state == RecoveryLogin.State.NONE ? null : state.name().toLowerCase(Locale.ROOT);
    }

    /** 印の知らせの期間（「15 分」）。テンプレートは静的なクラスを参照しないのでモデルで渡す（Bolt 33f）。 */
    @ModelAttribute("recoveryLoginLifetime")
    public String recoveryLoginLifetime() {
        return DurationDisplay.length(SessionPolicy.RECOVERY_LOGIN_LIFETIME);
    }
}
