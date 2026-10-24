package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.shared.acceptance.ClockSteps;
import com.example.cargotracker.shared.acceptance.EventDeliverySteps;
import io.cucumber.java.ja.前提;

/**
 * 受入シナリオの背景の共通の前提（Bolt 37f2。#50）。荷主が見積依頼を提出し、営業担当者が審査して見積りを提示し、荷主が詳細経路設計へ
 * 進むと回答して、その依頼（DE-16）が購読するコンテキストに配信されたところまでを 1 つの前提にまとめる。経路設計・予約・見積りの
 * 8 つの feature が同じに持っていた 10 ステップの写し。
 *
 * <p>中で決める値（シナリオに書かない）: 提出は 2026-10-05T01:00:00Z（輸送条件は {@link TransportRequestSteps} の決め打ち: JPTYO →
 * NLRTM、希望到着期限 2026-11-02T00:00:00Z、一般貨物）、審査の確定は 03:00:00Z（版 1、根拠「契約条件を確認した」）、見積りの算出・社内
 * 承認・提示は 04:00:00Z。シナリオと比べる依頼の時刻と見積りの有効期限は引数で見せる（テスト戦略の置き場とステップ定義）。審査と回答の
 * 結果は、まとめる前と同じく確かめない。
 *
 * <p>同じシナリオの中では、ステップのクラスは同じインスタンスで注入される（cucumber-spring のシナリオの範囲）ので、後のステップは
 * ここで入れた状態（輸送要求、見積りの有効期限など）を引き継ぐ。
 */
public class RouteDesignRequestedSteps {

    private final ClockSteps clock;
    private final TransportRequestSteps transportRequests;
    private final TransportRequestReviewSteps reviews;
    private final QuotationSteps quotations;
    private final QuotationResponseSteps responses;
    private final EventDeliverySteps delivery;

    public RouteDesignRequestedSteps(
            ClockSteps clock,
            TransportRequestSteps transportRequests,
            TransportRequestReviewSteps reviews,
            QuotationSteps quotations,
            QuotationResponseSteps responses,
            EventDeliverySteps delivery) {
        this.clock = clock;
        this.transportRequests = transportRequests;
        this.reviews = reviews;
        this.quotations = quotations;
        this.responses = responses;
        this.delivery = delivery;
    }

    @前提("荷主担当者が {string} に、有効期限 {string} の見積り 1 について詳細経路設計へ進むと回答し、依頼が配信されている")
    public void 詳細経路設計の依頼が配信されている(String requestedAt, String expiresAt) {
        clock.現在時刻が_である("2026-10-05T01:00:00Z");
        transportRequests.荷主が必須条件をそろえた輸送条件を入力している();
        transportRequests.荷主が入力した輸送条件を提出する();
        clock.現在時刻が_である("2026-10-05T03:00:00Z");
        reviews.審査を確定する(1, "契約条件を確認した");
        clock.現在時刻が_である("2026-10-05T04:00:00Z");
        quotations.有効期限を決めた標準の見積りを提示している(expiresAt);
        clock.現在時刻が_である(requestedAt);
        responses.詳細経路設計へ進むと回答する(1);
        delivery.発行されたドメインイベントが配信される();
    }
}
