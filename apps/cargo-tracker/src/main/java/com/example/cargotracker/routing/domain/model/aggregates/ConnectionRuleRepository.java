package com.example.cargotracker.routing.domain.model.aggregates;

import java.util.List;

/**
 * 接続時間規則のリポジトリ（送信ポート）。規則の管理の画面はまだなく、開発環境の仮の値だけを読む（DM-05 は W6）。
 */
public interface ConnectionRuleRepository {

    /** すべての規則を返す。判定時刻で有効かは制約適合判定が決める。 */
    List<ConnectionRule> findAll();
}
