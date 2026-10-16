package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 利用者の管理の照会。S-20 の一覧と詳細を読む（Bolt 34。US-16 の一部）。`password_hash` を読まない射影で受ける。Bolt 34b から操作者を
 * 受け、管理範囲（`user_assignment` の `COMPANY`。IA-INV-14）の企業の利用者だけを返す。照会は書かない（範囲外の試行の記録は
 * 確定の POST でだけ controller が {@code AdministrationScopeDenialService} で書く）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserAdministrationQueryService {

    private final UserRepository users;
    private final UserAssignmentRepository assignments;

    public UserAdministrationQueryService(UserRepository users, UserAssignmentRepository assignments) {
        this.users = users;
        this.assignments = assignments;
    }

    /** 管理範囲の企業の利用者の一覧の行を、所属企業の名前・表示名の順に返す。システム管理者でなければ空。 */
    public List<UserSummary> list(AuthenticatedActor operator) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return List.of();
        }
        return users.findSummariesIn(assignments.scopeOf(operator.userId()).companies());
    }

    /**
     * 詳細を探す。管理範囲の外の利用者は範囲外として返す。管理範囲を先に読み、いない利用者と範囲外の利用者で読み出しの量をそろえる
     * （応答の時間から範囲外の利用者の存在を見分けにくくする。Bolt 34b の開発レビュー）。
     */
    public UserLookup detail(AuthenticatedActor operator, UserId id) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return new UserLookup.NotPermitted();
        }
        AdministrationScope scope = assignments.scopeOf(operator.userId());
        Optional<UserDetail> found = users.findDetail(id);
        if (found.isEmpty()) {
            return new UserLookup.NotFound();
        }
        if (!scope.covers(found.get().companyId())) {
            return new UserLookup.OutOfScope();
        }
        return new UserLookup.Found(found.get());
    }
}
