# language: ja
@ui @manual
機能: ユーザーマニュアルの画面のキャプチャを撮る
  ユーザーマニュアル（docs/manual）の画面のキャプチャを、本番と同じプロファイルとシナリオが作るダミーのデータで撮る。
  `./gradlew manualScreenshots` だけで動き、docs/manual/assets/<章番号>-<名前>.png に置く（手作業で PNG を置かない）。
  主成功の流れに沿って画面を開き、各章の節が説明する状態で撮る。受入条件は確かめない（受入条件は各機能のシナリオが確かめる）。

  シナリオ: 主成功の流れに沿って各章の画面を撮る
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
    # 6 章 受付一覧・審査・見積りの作成と提示
    かつ 画面 "/staff/transport-requests" を開く
    かつ 画面を "06-intake-list-review" として撮る
    かつ 画面 "/staff/transport-requests/{業務番号}" を開く
    かつ 画面を "06-review" として撮る
    かつ 営業担当者が提出した見積依頼の審査を確定している
    かつ 画面を "06-quotation-new" として撮る
    かつ キー操作だけで標準の料金明細と通貨と有効期限と経路方針を入れて算出する
    かつ 画面を "06-quotation-calculated" として撮る
    かつ キー操作だけで社内承認して提示する
    かつ 画面を "06-intake-list-presented" として撮る
    # 4 章 見積りへの回答と承認
    かつ 画面 "/customer/transport-requests/{業務番号}" を開く
    かつ 画面を "04-request-detail-quoted" として撮る
    かつ 画面 "/customer/transport-requests/{業務番号}/quotations/1/response" を開く
    かつ 画面を "04-response" として撮る
    かつ 荷主が提出した見積依頼の詳細画面を開く
    かつ キー操作だけで見積りへの回答を開き、詳細経路設計へ進む
    かつ 画面を "04-request-detail-routing" として撮る
    # 8 章 経路設計
    かつ 画面 "/staff/routing-cases" を開く
    かつ 画面を "08-routing-cases" として撮る
    かつ 経路設計者が案件一覧から提出した見積依頼の案件を開く
    かつ 画面を "08-routing-case-before-calculation" として撮る
    かつ キー操作だけで候補を算出する
    かつ 画面を "08-candidates" として撮る
    かつ 直行の航海の候補の確定へ進む
    かつ 画面を "08-confirmation" として撮る
    かつ 判断根拠 "直行で期限まで 3 日あり、積替えの遅れの心配がない。" を入れてキー操作だけで確定する
    かつ 画面を "08-confirmed" として撮る
    # 4 章 見積りと経路の承認
    かつ 荷主が見積依頼の詳細で確定した経路が届くのを待つ
    かつ 画面を "04-request-detail-awaiting-approval" として撮る
    かつ 画面 "/customer/transport-requests/{業務番号}/quotations/1/approval" を開く
    かつ 画面を "04-approval" として撮る
    かつ 荷主が提出した見積依頼の詳細画面を開く
    かつ キー操作だけで見積りと経路の承認を開き、承認する
    かつ 画面を "04-request-detail-approved" として撮る
    # 7 章 本予約の確定と予約一覧
    かつ 画面 "/staff/transport-requests" を開く
    かつ 画面を "07-intake-list-ready-to-book" として撮る
    かつ 営業担当者が受付一覧の予約の確定待ちから提出した見積依頼の見積り 1 の本予約の確定を開く
    かつ 画面を "07-booking-new" として撮る
    かつ キー操作だけで契約条件と照合したことを示して本予約を確定する
    かつ 予約の詳細を更新して追跡の開始が完了するのを待つ
    かつ 画面を "07-booking-detail" として撮る
    かつ 画面 "/staff/bookings" を開く
    かつ 画面を "07-booking-list" として撮る
    # 9 章 追跡管理
    かつ 追跡管理者がホームを開く
    かつ 画面を "09-tracking-list" として撮る
    かつ キー操作だけで追跡一覧の先頭の追跡の詳細を開く
    かつ 画面を "09-tracking-detail" として撮る
    かつ キー操作だけで追跡の詳細から実績の登録を開く
    かつ 画面を "09-milestone-new" として撮る
    かつ キー操作だけで実績 "集荷" を場所 "JPTYO"・発生時刻 "2026-10-01 09:00"・出典 "現場記録" の参照 "F-118" で登録する
    かつ 画面を "09-tracking-detail-registered" として撮る
    # 5 章 予約一覧と追跡の照会
    かつ 画面 "/customer/bookings" を開く
    かつ 画面を "05-bookings" として撮る
    かつ 画面 "/customer/tracking-records" を開く
    かつ 画面を "05-tracking-list" として撮る
    かつ 画面 "/customer/tracking-records/{追跡番号}" を開く
    かつ 画面を "05-tracking-result" として撮る
    かつ 画面 "/customer/tracking-records/CTZZZZZZZZZZZZ" を開く
    かつ 画面を "05-tracking-not-found" として撮る
    # 10 章 KPI 計測記録
    かつ 画面 "/staff/kpi-observations" を開く
    かつ 画面を "10-kpi-observations" として撮る
