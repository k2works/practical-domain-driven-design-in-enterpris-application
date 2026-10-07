package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 経路版の状態。値の名前はデータモデルの `route_version.status` と同じにする。
 * Bolt 17 は作成中と候補提示済みだけを使う。要専門家判断・確定は US-07、再設計要・旧版は US-07・US-08 で使う。
 */
public enum RouteVersionStatus {
    /** 作成中（候補を算出する前）。 */
    DRAFT,
    /** 候補提示済み。 */
    CANDIDATES_PRESENTED,
    /** 要専門家判断。 */
    EXPERT_REVIEW,
    /** 確定。 */
    CONFIRMED,
    /** 再設計要。 */
    REDESIGN_REQUIRED,
    /** 旧版。 */
    SUPERSEDED
}
