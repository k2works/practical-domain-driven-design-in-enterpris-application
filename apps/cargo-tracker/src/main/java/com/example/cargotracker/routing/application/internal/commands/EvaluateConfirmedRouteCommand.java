package com.example.cargotracker.routing.application.internal.commands;

import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import java.util.Objects;

/**
 * 外部情報の更新を受けて確定した経路版を評価するコマンド（US-07 AC4。Bolt 37d2）。利用者の操作ではないので、操作者と期待版を持たない
 * （読み込んだ案件の版で競合を検知する）。外部原本の取込（DE-12）の listener が使う（W7 でつなぐ。今は受入テストだけが呼ぶ）。
 *
 * @param number 案件番号
 */
@Command
public record EvaluateConfirmedRouteCommand(RoutingCaseNumber number) {

    public EvaluateConfirmedRouteCommand {
        Objects.requireNonNull(number, "number");
    }
}
