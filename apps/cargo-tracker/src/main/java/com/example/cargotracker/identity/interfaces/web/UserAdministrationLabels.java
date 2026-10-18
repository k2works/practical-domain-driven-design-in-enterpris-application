package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S-20 の画面（一覧・詳細・確認）で共通に使う名前とモデルの属性の名前（UI 設計 S-20。Bolt 34f）。ドメインに画面の言葉を入れないため画面の層に置く。
 * package-private の static のメソッドはテンプレートの {@code T(...)} から見えないので、controller が名前をモデルに渡す
 * （前例 {@code TransportRequestLabels}）。
 */
final class UserAdministrationLabels {

    /** フォームが持つ版のモデルの属性の名前（S-20 の画面で共通）。 */
    static final String VERSION_ATTRIBUTE = "version";

    /** レイアウトがナビの現在の項目を読むモデルの属性の名前。 */
    static final String ACTIVE_ATTRIBUTE = "active";

    /** レイアウトのナビの現在の項目（S-20 の画面で共通）。 */
    static final String ACTIVE = "users";

    private UserAdministrationLabels() {}

    /** 利用状態の名前。 */
    static String status(UserStatus status) {
        return switch (status) {
            case ACTIVE -> "利用中";
            case SUSPENDED -> "利用停止";
        };
    }

    /** 停止の理由の名前。 */
    static String suspensionReason(SuspensionReason reason) {
        return switch (reason) {
            case USAGE_ENDED -> "利用終了";
            case TRANSFERRED -> "異動";
            case EMERGENCY -> "緊急停止";
        };
    }

    /** 企業の種類の名前。 */
    static String companyKind(CompanyKind kind) {
        return switch (kind) {
            case SHIPPER -> "荷主企業";
            case CONSIGNEE -> "荷受人の企業";
            case OPERATOR -> "運用する会社";
        };
    }

    /** 役割の表示名を {@link Role} の宣言の順に「・」でつなぐ。なければ「なし」。 */
    static String roles(Set<Role> roles) {
        if (roles.isEmpty()) {
            return "なし";
        }
        return order(roles).stream().map(Role::displayName).collect(Collectors.joining("・"));
    }

    /** 役割を {@link Role} の宣言の順に並べる。 */
    static List<Role> order(Set<Role> roles) {
        return roles.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(roles));
    }
}
