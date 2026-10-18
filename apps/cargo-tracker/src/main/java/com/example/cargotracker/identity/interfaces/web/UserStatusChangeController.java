package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.StatusChangeCheck;
import com.example.cargotracker.identity.application.internal.commandservices.StatusChangeOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserStatusChangeService;
import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.ErrorMessage;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Operation;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Retry;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Section;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-20 の利用状態の変更（Bolt 34d、US-16 AC5。Bolt 34f で一覧・詳細の controller から分けた）。詳細の利用状態の領域から理由を選んで利用を
 * 停止し、停止中の利用者を再開する確認の領域（役割の変更と同じ確認と確定の形）。詳細の読み出しと拒否の共通の枝は詳細の部品
 * {@link UserDetailPage} が受け持つ。
 */
@Controller
public class UserStatusChangeController {

    /** 利用状態の変更の確認の URL（GET で確認、POST で確定。Bolt 34d）。 */
    static final String STATUS_CONFIRMATION_URL =
            UserAdministrationController.USERS_URL + "/{id}/status-changes/confirmation";

    /** 停止の完了の知らせ（UI 設計 S-20。Bolt 34d）。 */
    static final String SUSPENDED_NOTICE = "利用を停止しました。相手の次の操作から使えなくなります。";

    /** 再開の完了の知らせ（UI 設計 S-20。Bolt 34d）。 */
    static final String REACTIVATED_NOTICE = "利用を再開しました。相手は次のログインから使えます。";

    private final UserStatusChangeService statusChanges;
    private final UserDetailPage page;

    UserStatusChangeController(UserStatusChangeService statusChanges, UserDetailPage page) {
        this.statusChanges = statusChanges;
        this.page = page;
    }

    @GetMapping(STATUS_CONFIRMATION_URL)
    public String confirm(
            @PathVariable String id,
            @RequestParam String status,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = page.find(id, actor, false);
        StatusRequest request = StatusRequest.parse(status, reason);
        return switch (statusChanges.check(actor, user.id(), version, request.to(), request.reason())) {
            case StatusChangeCheck.Acceptable _ -> showConfirmation(user, request, version, actor, model);
            case StatusChangeCheck.Refused(var refusal) ->
                refuse(user.id(), request, version, actor, model, refusal, false);
        };
    }

    /**
     * 利用状態の変更の確定。状態と版の値は、先に詳細を読んで管理範囲を確かめてから解釈する（値の誤りの 400 を範囲の判定より先にすると、
     * 範囲外の利用者への確定の試行が記録に残らない。Bolt 34d の開発レビュー）。
     */
    @PostMapping(STATUS_CONFIRMATION_URL)
    public String change(
            @PathVariable String id,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "version", required = false) String versionValue,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        // 役割の変更と同じく、確定も先に詳細を読み、管理範囲の外なら試行を記録して 404 にする（書き手は詳細の部品で、確定のときだけ）
        UserId target = page.find(id, actor, true).id();
        StatusRequest request = StatusRequest.parse(status, reason);
        long version = UserAdministrationParameters.version(versionValue);
        return switch (statusChanges.change(actor, target, version, request.to(), request.reason())) {
            case StatusChangeOutcome.Changed _ -> {
                redirectAttributes.addFlashAttribute(
                        "notice", request.to() == UserStatus.SUSPENDED ? SUSPENDED_NOTICE : REACTIVATED_NOTICE);
                yield "redirect:" + UserAdministrationController.USERS_URL + "/" + target.value();
            }
            case StatusChangeOutcome.Refused(var refusal) ->
                refuse(target, request, version, actor, model, refusal, true);
        };
    }

    /** 受け付けなかった結果の画面。規則の拒否は選んだ理由のまま示す（ほかの枝は詳細の部品）。 */
    private String refuse(
            UserId target,
            StatusRequest request,
            long version,
            AuthenticatedActor actor,
            Model model,
            UserAdministrationRefusal<StatusChangeRejection> refusal,
            boolean recordDenial) {
        return page.refuse(
                target,
                actor,
                model,
                refusal,
                recordDenial,
                new Operation<>(
                        Section.STATUS,
                        (user, rejection) -> new Retry(
                                user.roles(),
                                request.reason(),
                                // 同じ状態の拒否は、ほかの管理者が先に同じ変更を確定したときに起きる。次の操作が古い版で拒まれないよう、最新の版で示す
                                rejection == StatusChangeRejection.NO_CHANGE ? user.version() : version,
                                errorMessage(rejection, user.status()))));
    }

    private String showConfirmation(
            UserDetail user, StatusRequest request, long version, AuthenticatedActor actor, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("suspending", request.to() == UserStatus.SUSPENDED);
        model.addAttribute("currentStatus", UserAdministrationLabels.status(user.status()));
        model.addAttribute(
                "reasonName",
                request.reason().map(UserAdministrationLabels::suspensionReason).orElse(null));
        model.addAttribute("status", request.to().name());
        model.addAttribute(
                "reason", request.reason().map(SuspensionReason::name).orElse(null));
        model.addAttribute(UserAdministrationLabels.VERSION_ATTRIBUTE, version);
        model.addAttribute("operator", Role.SYSTEM_ADMIN.displayName() + " " + actor.displayName());
        model.addAttribute(UserAdministrationLabels.ACTIVE_ATTRIBUTE, UserAdministrationLabels.ACTIVE);
        return "identity/users/status-confirmation";
    }

    /**
     * 利用状態の変更の拒否の文言（UI 設計 S-20 の表。Bolt 34d）。自分と監督の役割の拒否は詳細の案内と同じ文言。停止の理由の選び方の誤り
     * だけ理由の欄へリンクする。
     */
    static ErrorMessage errorMessage(StatusChangeRejection rejection, UserStatus current) {
        String text =
                switch (rejection) {
                    case SELF_CHANGE -> UserDetailPage.SELF_STATUS_NOTE;
                    case OVERSIGHT_ROLE -> UserDetailPage.OVERSIGHT_STATUS_NOTE;
                    case NO_CHANGE -> "利用状態は既に" + UserAdministrationLabels.status(current) + "です。最新の内容を確かめてください。";
                    case REASON_REQUIRED -> "停止の理由を選んでください。";
                };
        return new ErrorMessage(text, rejection == StatusChangeRejection.REASON_REQUIRED);
    }

    /**
     * 利用状態の変更の送信（状態と理由の名前を値にしたもの）。
     *
     * @param to 変更後の利用状態
     * @param reason 停止の理由
     */
    private record StatusRequest(UserStatus to, Optional<SuspensionReason> reason) {

        /** 知らない状態・理由の名前と、再開に理由を付けた送信は 400（役割の名前と同じ扱い）。 */
        static StatusRequest parse(String status, String reason) {
            if (status == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            UserStatus to;
            try {
                to = UserStatus.valueOf(status);
            } catch (IllegalArgumentException _) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            Optional<SuspensionReason> parsed = UserAdministrationParameters.suspensionReason(reason);
            if (to == UserStatus.ACTIVE && parsed.isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            return new StatusRequest(to, parsed);
        }
    }
}
