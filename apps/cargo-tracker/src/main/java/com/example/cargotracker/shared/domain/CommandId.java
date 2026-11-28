package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * コマンド ID。変更コマンドを識別し、同じコマンド ID の再送を見分ける（ARCH-HO-01）。画面はフォームを開くたびに新しいコマンド ID を
 * 発行し、隠し項目で送る（Bolt 24）。
 *
 * @param value 識別子
 */
@ValueObject
public record CommandId(UUID value) {

    public CommandId {
        Objects.requireNonNull(value, "value");
    }

    /** 新しいコマンド ID を発行する。 */
    public static CommandId random() {
        return new CommandId(UUID.randomUUID());
    }
}
