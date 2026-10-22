package com.example.cargotracker.quotation.interfaces.api;

import com.example.cargotracker.shared.domain.UtcInstant;

/**
 * 再設計要の伝達の依頼（見積りの公開 API。Bolt 37e）。見積りは経路設計の案件 ID を持たないので、経路版を案件番号と経路版番号で指す
 * （D-4）。値の検査はアダプターが行い、不正なら理由で返す（再配信しても変わらない依頼を例外にしない。ADR-014）。
 *
 * @param routingCaseNumber 案件番号（例: {@code RC-2026-0001}）
 * @param routeVersionNo 経路版番号（1 から）
 * @param requiredAt 経路設計が再設計要にした時刻
 */
public record RouteRedesignNotificationRequest(String routingCaseNumber, int routeVersionNo, UtcInstant requiredAt) {}
