# language: ja
@ui @manual
機能: ユーザーマニュアルの画面のキャプチャを撮る
  ユーザーマニュアル（docs/manual）の画面のキャプチャを、本番と同じプロファイルとシナリオが作るダミーのデータで撮る。
  `./gradlew manualScreenshots` だけで動き、docs/manual/assets/<章番号>-<名前>.png に置く（手作業で PNG を置かない）。
  主成功の流れに沿って画面を開き、各章の節が説明する状態で撮る。受入条件は確かめない（受入条件は各機能のシナリオが確かめる）。

  シナリオ: 主成功の流れに沿って荷主と共通の画面を撮る
    前提 経路の比較に使う航海と接続時間規則がある
    # 2 章 ログインと共通の画面
    もし 画面 "/login" を開く
    かつ 画面を "02-login" として撮る
    かつ 画面 "/session-expired" を開く
    かつ 画面を "02-session-expired" として撮る
    # 3 章 見積依頼（作成の前の空の画面、提出の後の詳細、一覧）
    かつ 画面 "/customer/transport-requests/new" を開く
    かつ 画面を "03-request-new" として撮る
    かつ 荷主が見積依頼を提出している
    かつ 画面を "03-request-detail-submitted" として撮る
    かつ 画面 "/customer/transport-requests" を開く
    かつ 画面を "03-request-list" として撮る
    # 2 章 権限がない画面（荷主担当者のまま社内の画面を開く）
    かつ ログインしたまま画面 "/staff/transport-requests" を開く
    かつ 画面を "02-forbidden" として撮る
    # 4 章 見積りへの回答と承認
    かつ 営業担当者が提出した見積依頼の見積りを算出して提示している
    かつ 画面 "/customer/transport-requests/{業務番号}" を開く
    かつ 画面を "04-request-detail-quoted" として撮る
    かつ 画面 "/customer/transport-requests/{業務番号}/quotations/1/response" を開く
    かつ 画面を "04-response" として撮る
    かつ 荷主が提出した見積依頼の詳細画面を開く
    かつ キー操作だけで見積りへの回答を開き、詳細経路設計へ進む
    かつ 画面を "04-request-detail-routing" として撮る
    かつ 経路設計者が案件一覧から提出した見積依頼の案件を開く
    かつ キー操作だけで候補を算出する
    かつ 直行の航海の候補の確定へ進む
    かつ 判断根拠 "直行で期限まで 3 日あり、積替えの遅れの心配がない。" を入れてキー操作だけで確定する
    かつ 荷主が見積依頼の詳細で確定した経路が届くのを待つ
    かつ 画面を "04-request-detail-awaiting-approval" として撮る
    かつ 画面 "/customer/transport-requests/{業務番号}/quotations/1/approval" を開く
    かつ 画面を "04-approval" として撮る
    かつ 荷主が提出した見積依頼の詳細画面を開く
    かつ キー操作だけで見積りと経路の承認を開き、承認する
    かつ 画面を "04-request-detail-approved" として撮る
    # 5 章 予約一覧と追跡の照会
    かつ 営業担当者が受付一覧の予約の確定待ちから提出した見積依頼の見積り 1 の本予約の確定を開く
    かつ キー操作だけで契約条件と照合したことを示して本予約を確定する
    かつ 予約の詳細を更新して追跡の開始が完了するのを待つ
    かつ 追跡管理者がホームを開く
    かつ キー操作だけで追跡一覧の先頭の追跡の詳細を開く
    かつ キー操作だけで追跡の詳細から実績の登録を開く
    かつ キー操作だけで実績 "集荷" を場所 "JPTYO"・発生時刻 "2026-10-01 09:00"・出典 "現場記録" の参照 "F-118" で登録する
    かつ 画面 "/customer/bookings" を開く
    かつ 画面を "05-bookings" として撮る
    かつ 画面 "/customer/tracking-records" を開く
    かつ 画面を "05-tracking-list" として撮る
    かつ 画面 "/customer/tracking-records/{追跡番号}" を開く
    かつ 画面を "05-tracking-result" として撮る
    かつ 画面 "/customer/tracking-records/CTZZZZZZZZZZZZ" を開く
    かつ 画面を "05-tracking-not-found" として撮る
