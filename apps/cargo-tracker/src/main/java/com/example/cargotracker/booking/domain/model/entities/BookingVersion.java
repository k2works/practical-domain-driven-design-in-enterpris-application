package com.example.cargotracker.booking.domain.model.entities;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 予約版。貨物予約のある時点の合意内容（見積り、経路版、貨物）。予約版番号で識別し、変えない（B-INV-08）。変更は新しい予約版になる（US-05）。
 *
 * @param versionNo 予約版番号（1 から）
 * @param terms 予約条件（確定の時点の見積りの写し）
 * @param confirmedBy 確定した営業担当者の利用者 ID
 * @param committedAt commit 時刻（判定と記録に同じ値を使う。ADR-016）
 */
@Entity
public record BookingVersion(int versionNo, BookingTerms terms, UUID confirmedBy, UtcInstant committedAt) {

    public BookingVersion {
        if (versionNo < 1) {
            throw new IllegalArgumentException("予約版番号は 1 以上です: " + versionNo);
        }
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(confirmedBy, "confirmedBy");
        Objects.requireNonNull(committedAt, "committedAt");
    }
}
