package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * session の ID。ほかの session を失効させるときに残す、今の session の ID（Bolt 33e。文字列で受け渡していたのを値にした）。
 * session の ID は session を乗っ取る鍵になるので、ログに出さないよう {@link #toString()} は値を伏せる。
 */
@ValueObject
public record SessionId(String value) {

    public SessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("session の ID がない");
        }
    }

    @Override
    public String toString() {
        return "SessionId[***]";
    }
}
