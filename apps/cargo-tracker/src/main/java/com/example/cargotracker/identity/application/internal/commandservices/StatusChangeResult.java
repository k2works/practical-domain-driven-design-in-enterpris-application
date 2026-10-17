package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import java.util.Objects;

/**
 * 利用状態の変更の結果（{@link UserStatusChangeService}。Bolt 34d）。画面は種類で行き先と文言を選ぶ。役割の変更の結果
 * （{@link RoleChangeResult}）とは別の型にし、共用しない。
 */
public sealed interface StatusChangeResult {

    /**
     * 利用状態を変えた（版を上げ、監査記録を書いた）。
     *
     * @param version 更新した後の版
     */
    record Changed(long version) implements StatusChangeResult {}

    /** 確かめるだけの呼び出し（{@link UserStatusChangeService#check}）で、この変更を受け付けられる。何も書いていない。 */
    record Acceptable() implements StatusChangeResult {}

    /**
     * 判定の規則で受け付けない。何も書いていない。
     *
     * @param rejection 理由
     */
    record Rejected(StatusChangeRejection rejection) implements StatusChangeResult {

        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /** ほかの更新（役割の変更を含む）が先に入った（画面が持つ版が古い）。何も書いていない。 */
    record Stale() implements StatusChangeResult {}

    /** 利用者（またはその所属企業）がいない。 */
    record NotFound() implements StatusChangeResult {}

    /** 操作者がシステム管理者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted() implements StatusChangeResult {}

    /** 対象の利用者が操作者の管理範囲の外（IA-INV-14）。何も書いていない（試行の記録は controller が書く）。 */
    record OutOfScope() implements StatusChangeResult {}
}
