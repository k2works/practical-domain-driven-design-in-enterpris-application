/**
 * 予約サガの状態とリポジトリ（送信ポート）。状態（処理中・完了・失敗・有人確認要）を予約の 1 か所に持つ（ADR-015）。
 * サガを進める処理（追跡の開始の結果の受け取り、処理中の滞留の検出）は {@code application.sagas} に置く（Bolt 25・W8）。
 */
package com.example.cargotracker.booking.domain.model.sagas;
