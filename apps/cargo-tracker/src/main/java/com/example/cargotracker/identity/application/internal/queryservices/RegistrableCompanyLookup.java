package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.valueobjects.RegistrableCompany;
import java.util.Objects;

/**
 * 登録できる企業の照会の結果（{@link UserAdministrationQueryService#registrableCompany}。Bolt 38b3）。判定の順は登録のサービスと同じで、
 * 範囲外の企業は状態によらず {@link OutOfScope}（範囲外の企業の状態を示さない）。画面は {@link NotFound} と {@link OutOfScope} を同じ 404
 * にし、{@link Inactive} と {@link NoAssignableRoles} は企業を選ぶ画面に戻して文言を示す。登録の拒否の {@code CompanyNotFound}・
 * {@code CompanyOutOfScope}・{@code InactiveCompany} に当たり、{@link NoAssignableRoles} は {@code Rejected(UNFIT_FOR_COMPANY)} に当たる。
 */
public sealed interface RegistrableCompanyLookup {

    /**
     * 登録できる企業が見つかった。
     *
     * @param company 登録できる企業
     */
    record Found(RegistrableCompany company) implements RegistrableCompanyLookup {

        public Found {
            Objects.requireNonNull(company, "company");
        }
    }

    /** 企業がない。 */
    record NotFound() implements RegistrableCompanyLookup {}

    /** 企業はあるが、操作者の管理範囲の外（IA-INV-14）。 */
    record OutOfScope() implements RegistrableCompanyLookup {}

    /** 管理範囲の企業だが、無効。 */
    record Inactive() implements RegistrableCompanyLookup {}

    /** 管理範囲の有効な企業だが、その種類の利用者に付けられる役割がない（荷受人の企業。IA-INV-13）。 */
    record NoAssignableRoles() implements RegistrableCompanyLookup {}

    /** 操作者がシステム管理者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted() implements RegistrableCompanyLookup {}
}
