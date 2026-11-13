package com.example.cargotracker.tracking.domain.model.entities;

import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;

/**
 * 進捗。有人案件の進捗の更新の記録で、案件の中で進捗番号で識別する（Bolt 40-5。表 {@code service_case_progress}）。追記専用。
 *
 * @param progressNo 進捗番号（案件の中で 1 から）
 * @param content 進捗の内容
 * @param nextUpdateAt 次回更新日時（指定がなければ空）
 * @param updatedBy 更新者
 * @param updatedAt 更新時刻
 */
@Entity
public record CaseProgress(
        int progressNo, String content, Optional<UtcInstant> nextUpdateAt, UserId updatedBy, UtcInstant updatedAt) {

    /** 進捗の内容の長さの上限（表の列と同じ）。 */
    public static final int CONTENT_LIMIT = 4000;

    public CaseProgress {
        if (progressNo < 1) {
            throw new IllegalArgumentException("進捗番号は 1 から: " + progressNo);
        }
        if (content == null || content.isBlank() || content.length() > CONTENT_LIMIT) {
            throw new IllegalArgumentException("進捗の内容は空でなく " + CONTENT_LIMIT + " 文字まで");
        }
        Objects.requireNonNull(nextUpdateAt, "nextUpdateAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
