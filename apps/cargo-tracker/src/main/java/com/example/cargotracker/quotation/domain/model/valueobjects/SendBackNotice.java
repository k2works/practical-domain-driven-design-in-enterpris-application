package com.example.cargotracker.quotation.domain.model.valueobjects;

import java.util.Objects;

/**
 * 荷主に見せる差戻しの知らせ。差戻しの審査記録のうち、荷主に開示する理由と不足事項だけを持つ。
 * 判断者と審査の確定の根拠は社内の記録として見せない（2026-10-03 の人の決定。Bolt 6）。
 *
 * @param reason 差戻しの理由
 * @param missingItems 不足事項（任意。なければ null）
 */
public record SendBackNotice(String reason, String missingItems) {

    public SendBackNotice {
        Objects.requireNonNull(reason, "reason");
    }
}
