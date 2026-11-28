package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import java.util.Objects;

/**
 * 利用者の探索の結果（{@link UserAdministrationQueryService#detail}。Bolt 34b）。画面は種類で応答を選ぶ（範囲外は存在しない利用者と同じ
 * 404 にし、試行を記録する）。
 */
public sealed interface UserLookup {

    /**
     * 管理範囲の利用者が見つかった。
     *
     * @param detail 詳細
     */
    record Found(UserDetail detail) implements UserLookup {

        public Found {
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** 利用者がいない。 */
    record NotFound() implements UserLookup {}

    /** 利用者はいるが、操作者の管理範囲の外（IA-INV-14）。 */
    record OutOfScope() implements UserLookup {}

    /** 操作者がシステム管理者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted() implements UserLookup {}
}
