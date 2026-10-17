package com.example.cargotracker.identity.application.internal.commandservices;

import java.util.Objects;

/**
 * 利用者の管理（役割の変更 {@link UserRoleChangeService}・利用状態の変更 {@link UserStatusChangeService}）で受け付けなかった理由
 * （Bolt 34e で 2 つの結果の型の拒否を寄せた）。画面は種類で行き先と文言を選ぶ。どの拒否でも何も書いていない（範囲外の試行の記録は
 * controller が書く）。
 *
 * @param <R> 判定の規則の拒否の型（役割の変更なら {@code RoleChangeRejection}、利用状態の変更なら {@code StatusChangeRejection}）
 */
// R は Rejected だけが値として持つ。ほかの拒否も型の引数を持たせ、役割の変更と利用状態の変更の拒否を型で取り違えないようにする
@SuppressWarnings("java:S2326")
public sealed interface UserAdministrationRefusal<R> {

    /** 操作者がシステム管理者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted<R>() implements UserAdministrationRefusal<R> {}

    /** 利用者（またはその所属企業）がいない。 */
    record NotFound<R>() implements UserAdministrationRefusal<R> {}

    /** 対象の利用者が操作者の管理範囲の外（Bolt 34b。IA-INV-14）。 */
    record OutOfScope<R>() implements UserAdministrationRefusal<R> {}

    /**
     * 判定の規則で受け付けない。
     *
     * @param rejection 規則の拒否（理由など）
     */
    record Rejected<R>(R rejection) implements UserAdministrationRefusal<R> {

        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /** ほかの更新が先に入った（画面が持つ版が古い）。 */
    record Stale<R>() implements UserAdministrationRefusal<R> {}
}
