package com.example.cargotracker.tracking.domain.model.entities;

import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;

/**
 * 回答。有人案件への回答と、それに対する顧客の回答（未解決など）の記録で、案件の中で回答番号で識別する（Bolt 40-5。表 {@code service_case_answer}）。
 * 回答の内容は書き換えない。顧客の回答は、再開のときに最後の回答へ足す（SC-INV-03: 過去の回答履歴を保持する）。
 *
 * @param answerNo 回答番号（案件の中で 1 から）
 * @param content 回答の内容
 * @param answeredBy 回答者
 * @param answeredAt 回答時刻
 * @param customerResponse 顧客の回答（なければ空）
 * @param customerRespondedAt 顧客が回答した時刻（顧客の回答と同じときだけ持つ）
 */
@Entity
public record CaseAnswer(
        int answerNo,
        String content,
        UserId answeredBy,
        UtcInstant answeredAt,
        Optional<String> customerResponse,
        Optional<UtcInstant> customerRespondedAt) {

    /** 回答の長さの上限（表の列と同じ）。 */
    public static final int CONTENT_LIMIT = 4000;

    public CaseAnswer {
        if (answerNo < 1) {
            throw new IllegalArgumentException("回答番号は 1 から: " + answerNo);
        }
        if (content == null || content.isBlank() || content.length() > CONTENT_LIMIT) {
            throw new IllegalArgumentException("回答の内容は空でなく " + CONTENT_LIMIT + " 文字まで");
        }
        Objects.requireNonNull(answeredBy, "answeredBy");
        Objects.requireNonNull(answeredAt, "answeredAt");
        Objects.requireNonNull(customerResponse, "customerResponse");
        Objects.requireNonNull(customerRespondedAt, "customerRespondedAt");
        if (customerResponse.isPresent() != customerRespondedAt.isPresent()) {
            throw new IllegalArgumentException("顧客の回答とその時刻はそろえる: " + answerNo);
        }
    }

    /** 顧客の回答を足した回答（内容は書き換えない）。 */
    public CaseAnswer withCustomerResponse(String response, UtcInstant respondedAt) {
        if (response == null || response.isBlank() || response.length() > CONTENT_LIMIT) {
            throw new IllegalArgumentException("顧客の回答は空でなく " + CONTENT_LIMIT + " 文字まで");
        }
        return new CaseAnswer(
                answerNo, content, answeredBy, answeredAt, Optional.of(response), Optional.of(respondedAt));
    }
}
