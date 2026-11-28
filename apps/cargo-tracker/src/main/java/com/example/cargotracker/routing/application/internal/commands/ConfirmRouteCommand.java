package com.example.cargotracker.routing.application.internal.commands;

import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 経路設計者が判断根拠を記録して経路を確定するコマンド（US-07 AC1・AC2。Bolt 19）。
 * 承認者の役割は認証の主体から渡す（集約でも経路設計者かを確かめる。確認ポイント 6）。
 *
 * @param number 案件番号
 * @param candidateNo 選んだ候補番号
 * @param rationale 判断根拠（入力のまま。空・長すぎるは集約が拒否する）
 * @param expectedVersion 画面を開いたときの案件の版（違えば競合。Bolt 17 レビュー D-64）
 * @param approver 承認者（ログインした利用者）
 * @param routeDesigner 承認者が経路設計者の役割を持つか
 */
@Command
public record ConfirmRouteCommand(
        RoutingCaseNumber number,
        int candidateNo,
        String rationale,
        long expectedVersion,
        UserId approver,
        boolean routeDesigner) {

    public ConfirmRouteCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(approver, "approver");
    }
}
