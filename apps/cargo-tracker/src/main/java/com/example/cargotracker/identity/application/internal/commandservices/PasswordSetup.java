package com.example.cargotracker.identity.application.internal.commandservices;

import java.util.Objects;
import java.util.UUID;

/**
 * A-06 で設定できる password の形（{@link PasswordResetCompletionService#setupOf}。Bolt 38b4）。画面は初回の設定かで見出し・説明・title を
 * 選ぶ（用途の名前を画面に渡さない）。
 *
 * @param tokenId トークンの ID
 * @param initial 招待のリンクで初回の password を設定するか
 */
public record PasswordSetup(UUID tokenId, boolean initial) {

    public PasswordSetup {
        Objects.requireNonNull(tokenId, "tokenId");
    }
}
