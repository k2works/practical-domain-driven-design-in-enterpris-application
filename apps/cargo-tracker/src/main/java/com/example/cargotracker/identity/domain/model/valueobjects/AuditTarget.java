package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 監査記録の対象。対象の種類・ID・版と変更前後の要約（データモデル `audit_record.target_type`・`target_id`・
 * `target_version`・`before_state`・`after_state`。Bolt 34）。役割の変更の履歴は別の表を作らず、この値で残す（2026-10-16、human:kakimomokuri）。
 *
 * @param type 対象の種類（役割の変更と利用状態の変更（Bolt 34d）では {@code USER}）
 * @param id 対象の ID（役割の変更と利用状態の変更では利用者 ID）
 * @param version 対象の版（役割の変更と利用状態の変更では更新した後の版）
 * @param beforeState 変更前の要約（役割の名前、または利用状態の名前）
 * @param afterState 変更後の要約
 */
@ValueObject
public record AuditTarget(String type, String id, String version, String beforeState, String afterState) {

    public AuditTarget {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("監査記録の対象の種類がない");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("監査記録の対象の ID がない");
        }
    }
}
