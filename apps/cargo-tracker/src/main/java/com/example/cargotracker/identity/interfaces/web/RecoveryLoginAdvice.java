package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.queryservices.TotpEnrollmentQueryService;
import com.example.cargotracker.identity.domain.model.rules.SessionPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.RemainingRecoveryCodes;
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
 *
 * <p>印がある間は、まだ使っていない回復コードが少ないとき（{@link RemainingRecoveryCodes#isLow()}）だけ、残りの数をモデルに入れる
 * （Bolt 38d2。A-08 の本文と知らせが使う）。全画面に効く advice なので、照会はほかのコンテキストの {@code @WebMvcTest} の
 * slice にない。{@code ObjectProvider} で受け、なければ数を入れない。
 */
@ControllerAdvice
public class RecoveryLoginAdvice {

    /** レイアウトが読むモデルの属性の名前。値は {@code active}・{@code expired}・{@code null}（出さない）。 */
    static final String NOTICE = "recoveryLoginNotice";

    /** 残りの回復コード（少ないときだけ。値は {@link RemainingRecoveryCodes} か {@code null}）。A-08 と知らせが読む（Bolt 38d2）。 */
    static final String REMAINING = "remainingRecoveryCodes";

    private final ObjectProvider<Clock> clock;
    private final ObjectProvider<TotpEnrollmentQueryService> enrollment;

    /**
     * 時計は合成ルートの {@code Clock} を使い、ない画面の単体テストのコンテキスト（{@code @WebMvcTest}）ではシステムの時計にする。
     * 照会も同じく、ないコンテキストでは使わない。
     */
    public RecoveryLoginAdvice(ObjectProvider<Clock> clock, ObjectProvider<TotpEnrollmentQueryService> enrollment) {
        this.clock = clock;
        this.enrollment = enrollment;
    }

    @ModelAttribute(NOTICE)
    public String recoveryLoginNotice(HttpServletRequest request, Optional<AuthenticatedActor> actor) {
        RecoveryLogin.State state = stateOf(request, actor);
        return state == RecoveryLogin.State.NONE ? null : state.name().toLowerCase(Locale.ROOT);
    }

    @ModelAttribute(REMAINING)
    public RemainingRecoveryCodes remainingRecoveryCodes(
            HttpServletRequest request, Optional<AuthenticatedActor> actor) {
        TotpEnrollmentQueryService query = enrollment.getIfAvailable();
        if (query == null || stateOf(request, actor) == RecoveryLogin.State.NONE) {
            return null;
        }
        RemainingRecoveryCodes remaining =
                query.remainingRecoveryCodes(actor.orElseThrow().userId());
        return remaining.isLow() ? remaining : null;
    }

    private RecoveryLogin.State stateOf(HttpServletRequest request, Optional<AuthenticatedActor> actor) {
        return RecoveryLogin.stateOf(
                request.getSession(false),
                actor.orElse(null),
                clock.getIfAvailable(Clock::systemUTC).instant());
    }

    /** 印の知らせの期間（「15 分」）。テンプレートは静的なクラスを参照しないのでモデルで渡す（Bolt 33f）。 */
    @ModelAttribute("recoveryLoginLifetime")
    public String recoveryLoginLifetime() {
        return DurationDisplay.length(SessionPolicy.RECOVERY_LOGIN_LIFETIME);
    }
}
