package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 社内業務 Web の利用者の管理（S-20。Bolt 34、US-16 の一部）。システム管理者だけが開ける（`SecurityConfiguration` の
 * `/staff/users/**`）。一覧と詳細を示し、詳細で役割を選んで確認の領域へ進む。
 */
@Controller
public class UserAdministrationController {

    /** 一覧と詳細の URL。 */
    static final String USERS_URL = "/staff/users";

    /** レイアウトのナビの現在の項目。 */
    private static final String ACTIVE = "users";

    private final UserAdministrationQueryService queryService;

    public UserAdministrationController(UserAdministrationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping(USERS_URL)
    public String list(Model model) {
        model.addAttribute("users", queryService.list());
        model.addAttribute("active", ACTIVE);
        return "identity/users/list";
    }

    @GetMapping(USERS_URL + "/{id}")
    public String show(@PathVariable String id, Model model) {
        UserDetail user = detail(id);
        return showDetail(user, user.roles(), model);
    }

    private String showDetail(UserDetail user, Set<Role> selected, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("roleOptions", roleOptions(user, selected));
        model.addAttribute("companyKindName", companyKindName(user.companyKind()));
        model.addAttribute("active", ACTIVE);
        return "identity/users/detail";
    }

    private UserDetail detail(String id) {
        return parse(id).flatMap(queryService::detail).orElseThrow(UserAdministrationController::notFound);
    }

    private static java.util.Optional<UserId> parse(String id) {
        try {
            return java.util.Optional.of(new UserId(UUID.fromString(id)));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    /** 8 つの役割の選択肢。企業の種類に付けられない役割は、選べない旨を添える（判定はサーバーが正。IA-INV-13）。 */
    private static List<RoleOption> roleOptions(UserDetail user, Set<Role> selected) {
        Set<Role> unfit = RoleAssignmentPolicy.unfitRoles(user.companyKind(), Set.of(Role.values()));
        return Arrays.stream(Role.values())
                .map(role -> new RoleOption(role, selected.contains(role), !unfit.contains(role)))
                .toList();
    }

    /** 企業の種類の画面の名前（UI 設計 S-20）。 */
    static String companyKindName(CompanyKind kind) {
        return switch (kind) {
            case SHIPPER -> "荷主企業";
            case CONSIGNEE -> "荷受人の企業";
            case OPERATOR -> "運用する会社";
        };
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /**
     * 役割の選択肢。
     *
     * @param role 役割
     * @param selected 選んでいるか
     * @param fit 利用者の企業の種類に付けられるか
     */
    public record RoleOption(Role role, boolean selected, boolean fit) {}
}
