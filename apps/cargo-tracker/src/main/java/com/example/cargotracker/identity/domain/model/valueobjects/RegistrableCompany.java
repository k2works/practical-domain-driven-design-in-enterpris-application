package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import java.util.Objects;
import java.util.Set;

/**
 * 登録できる企業。S-20 の登録の画面が選択肢に出す企業の読み出しの射影（Bolt 38b3）。管理範囲の、有効で、付けられる業務の役割がある企業
 * だけを表す（荷受人の企業は出さない。IA-INV-13・IA-INV-14）。
 *
 * @param id 企業 ID
 * @param name 企業の名前
 * @param kind 企業の種類
 * @param assignableRoles その種類の利用者に付けられる業務の役割（空でない）
 */
@ValueObject
public record RegistrableCompany(CompanyId id, String name, CompanyKind kind, Set<Role> assignableRoles) {

    public RegistrableCompany {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        assignableRoles = Set.copyOf(assignableRoles);
        if (assignableRoles.isEmpty()) {
            throw new IllegalArgumentException("付けられる役割のない企業は登録できる企業にしない");
        }
    }
}
