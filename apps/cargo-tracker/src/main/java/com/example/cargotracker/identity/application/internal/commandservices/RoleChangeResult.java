package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import java.util.Objects;

/**
 * 役割の変更の結果（{@link UserRoleChangeService}。Bolt 34）。画面は種類で行き先と文言を選ぶ。
 */
public sealed interface RoleChangeResult {

    /**
     * 役割を変えた（版を上げ、監査記録を書いた）。
     *
     * @param version 更新した後の版
     */
    record Changed(long version) implements RoleChangeResult {}

    /** 確かめるだけの呼び出し（{@link UserRoleChangeService#check}）で、この変更を受け付けられる。何も書いていない。 */
    record Acceptable() implements RoleChangeResult {}

    /**
     * 判定の規則で受け付けない。何も書いていない。
     *
     * @param rejection 理由と示す役割
     */
    record Rejected(RoleChangeRejection rejection) implements RoleChangeResult {

        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /** ほかの更新が先に入った（画面が持つ版が古い）。何も書いていない。 */
    record Stale() implements RoleChangeResult {}

    /** 利用者（またはその所属企業）がいない。 */
    record NotFound() implements RoleChangeResult {}

    /** 操作者がシステム管理者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted() implements RoleChangeResult {}

    /** 対象の利用者が操作者の管理範囲の外（Bolt 34b。IA-INV-14）。何も書いていない（試行の記録は controller が書く）。 */
    record OutOfScope() implements RoleChangeResult {}
}
