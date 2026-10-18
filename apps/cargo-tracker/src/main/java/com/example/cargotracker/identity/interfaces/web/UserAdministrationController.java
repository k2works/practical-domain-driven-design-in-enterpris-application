package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.DetailForm;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 社内業務 Web の利用者の管理（S-20。Bolt 34、US-16 の一部）の一覧と詳細。システム管理者だけが開ける（`SecurityConfiguration` の
 * `/staff/users/**`。サービスも操作者を確かめる）。Bolt 34f で、役割の変更（{@link UserRoleChangeController}）と利用状態の変更
 * （{@link UserStatusChangeController}）を分け、詳細の読み出しと描画を詳細の部品 {@link UserDetailPage} に寄せた。
 */
@Controller
public class UserAdministrationController {

    /** 一覧と詳細の URL。 */
    static final String USERS_URL = "/staff/users";

    /** 一覧の利用状態の名前（テンプレートは利用状態の名前で引く）。 */
    private static final Map<String, String> STATUS_NAMES = Arrays.stream(UserStatus.values())
            .collect(Collectors.toUnmodifiableMap(UserStatus::name, UserAdministrationLabels::status));

    private final UserAdministrationQueryService queryService;
    private final UserDetailPage page;

    UserAdministrationController(UserAdministrationQueryService queryService, UserDetailPage page) {
        this.queryService = queryService;
        this.page = page;
    }

    @GetMapping(USERS_URL)
    public String list(AuthenticatedActor actor, Model model) {
        model.addAttribute("summaries", queryService.list(actor));
        model.addAttribute("statusNames", STATUS_NAMES);
        model.addAttribute(UserDetailPage.ACTIVE_ATTRIBUTE, UserDetailPage.ACTIVE);
        return "identity/users/list";
    }

    /**
     * 詳細。確認の領域の「戻る」は選んだ役割（または選んだ停止の理由）と画面の版を渡し、選んだまま、確認の前の版のまま示す。
     */
    @GetMapping(USERS_URL + "/{id}")
    public String show(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "version", required = false) Long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = page.find(id, actor, false);
        Set<Role> selected = roles == null ? user.roles() : UserAdministrationParameters.roles(roles);
        long formVersion = version == null ? user.version() : version;
        return page.render(
                DetailForm.of(user, selected, UserAdministrationParameters.suspensionReason(reason), formVersion),
                actor,
                model);
    }
}
