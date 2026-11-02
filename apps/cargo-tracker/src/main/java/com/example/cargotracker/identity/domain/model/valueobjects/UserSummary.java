package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 利用者の一覧の行。S-20 の一覧の 1 行の読み出しの射影（Bolt 34）。`password_hash` を持たない。Bolt 38h から担当企業を持つ。担当企業は担当範囲の
 * ある役割のときだけ集め（管理範囲は数えない。Bolt 38e の決定 (9)）、操作者の管理範囲の企業は名前、範囲の外は数だけにする（決定 (11)）。
 *
 * @param id 利用者 ID
 * @param companyName 所属企業の名前
 * @param displayName 表示名
 * @param email メールアドレス
 * @param status 利用状態
 * @param roles 役割
 * @param hasAssignmentScope 担当範囲のある役割か（射影を作るときに {@code RoleAssignmentPolicy} で 1 度だけ判定する。画面はこれを使う）
 * @param assignedCompanyNames 操作者の管理範囲の中の担当企業の名前（企業名の順）
 * @param assignedOutsideCount 操作者の管理範囲の外の担当企業の数
 */
@ValueObject
public record UserSummary(
        UserId id,
        String companyName,
        String displayName,
        EmailAddress email,
        UserStatus status,
        Set<Role> roles,
        boolean hasAssignmentScope,
        List<String> assignedCompanyNames,
        int assignedOutsideCount) {

    public UserSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(companyName, "companyName");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(status, "status");
        roles = Set.copyOf(roles);
        assignedCompanyNames = List.copyOf(assignedCompanyNames);
        if (assignedOutsideCount < 0) {
            throw new IllegalArgumentException("assignedOutsideCount");
        }    }
}
