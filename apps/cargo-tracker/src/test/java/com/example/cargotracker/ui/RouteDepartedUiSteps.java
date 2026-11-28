package com.example.cargotracker.ui;

import io.cucumber.java.ja.前提;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 割り当てた経路の最初の区間が出発した見積りの画面の層の準備（US-07 AC5、D-88。Bolt 37e4）。画面の層は実際の時計で動き、出発を待てない
 * ので、見積りの割り当てた区間の写しの出発をデータベースで過去にする（テストの準備だけに使う。前例は有効期限を過去にする準備）。経路設計の
 * 候補の区間は動かさない。画面を確かめるステップは Bolt 37e3 の {@link RouteRedesignUiSteps} を使う。
 */
public class RouteDepartedUiSteps {

    private final UiScenarioState state;
    private final JdbcTemplate jdbc;

    public RouteDepartedUiSteps(UiScenarioState state, JdbcTemplate jdbc) {
        this.state = state;
        this.jdbc = jdbc;
    }

    @前提("提出した見積依頼の割り当てた経路の最初の区間が出発している")
    public void 最初の区間が出発している() {
        int updated = jdbc.update(
                "UPDATE quotation.assigned_route_leg l SET departure_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'"
                        + " FROM quotation.quotation q JOIN quotation.transport_request t ON q.transport_request_id = t.id"
                        + " WHERE l.quotation_id = q.id AND t.request_number = ? AND q.quotation_no = 1 AND l.leg_no = 1",
                state.transportRequestNumber());
        if (updated != 1) {
            throw new IllegalStateException("最初の区間を出発させる見積りがありません: " + state.transportRequestNumber());
        }
    }
}
