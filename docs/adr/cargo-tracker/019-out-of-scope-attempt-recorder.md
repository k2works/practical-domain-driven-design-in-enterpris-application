---
type: ADR
title: "ADR-019: 担当範囲の外の試行の記録のポートを共有カーネルに置き、identity が実装する"
description: "業務担当者（営業担当者など）が担当企業の外の業務の対象を確定しようとした試行を、業務のコンテキストの画面の層から同期に監査記録へ書く。"
tags: [adr]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-29T04:55:37Z }
---

# ADR-019: 担当範囲の外の試行の記録のポートを共有カーネルに置き、identity が実装する

業務担当者（営業担当者など）が担当企業の外の業務の対象を確定しようとした試行を、業務のコンテキストの画面の層から同期に監査記録へ書く。
記録のポート `OutOfScopeAttemptRecorder` を共有カーネル（`shared.domain`）に置き、identity が実装する。共有カーネルに置く振る舞いの型は、このポート 1 つだけの例外とする。

日付: 2026-10-28

## ステータス

採用（方針は 2026-10-28 に human:kakimomokuri が Bolt 38f の開始準備で決めた（決定 (3)・(4)・(9)・(10)）。文面は Bolt 38f の終了報告の承認で確かめる）

## コンテキスト

- US-16 AC4: 業務担当者は担当企業の対象だけを扱い、担当範囲の外への試行は拒否して記録する（IA-INV-02・IA-INV-08）。Bolt 38e で担当企業の割り当て（`user_assignment` の `COMPANY`）を作り、Bolt 38f で見積りの側の社内の画面を絞る
- 監査記録はアクセス・監査（identity）のコンテキストが持つ。権限外のアクセスの試行は、ドメインイベントを経ずに同じ request の中で同期に書く（IA-INV-08）
- 業務のコンテキストが identity の公開 API を呼ぶと、identity → `quotation :: events` の依存があるので、見積りとの間で依存が循環する（Spring Modulith の検証が落ちる）
- 共有カーネル（`shared`、OPEN）には業務の意味を持たない基本型（値）だけを置いてきた（バックエンドアーキテクチャ、ADR-014）。振る舞いを持つポートの前例はない

## 決定

| 要素 | 決定 |
| :--- | :--- |
| ポートの置き場所 | `shared.domain.OutOfScopeAttemptRecorder`（`recordOutOfScope(AuthenticatedActor, ScopedResourceKind, String)`）。記録の対象の種類は列挙 `ScopedResourceKind`（`TRANSPORT_REQUEST`・`QUOTATION`・`ROUTING_CASE`・`TRACKING_RECORD`）で固定する。予約の側（38f2）は値を足さない。確定の POST の対象は見積りなので `QUOTATION` と「業務番号/見積り番号」で記録する（予約は確定の前にはまだないため）。38g で経路設計（`ROUTING_CASE`、案件番号）と追跡（`TRACKING_RECORD`、追跡番号）の値を足した |
| 実装 | `identity.interfaces.api.internal.OutOfScopeAttemptRecorderAdapter`（`@Component`）が `identity.application.internal.commandservices.AssignedScopeDenialService` に委ねる。合成ルートの `@Bean` にはしない（合成ルートが共有カーネルの型を返すと認証の規則に当たる） |
| 記録の値 | `action=ASSIGNED_SCOPE_DENIED`、`result=FAILURE`、`reason=OUT_OF_SCOPE`、対象の種類と識別（業務番号、見積りは「業務番号/見積り番号」）、版は空 |
| トランザクション | 記録は自分のトランザクションで書く。外側のトランザクションの中で呼ばれたら `IllegalStateException` で拒む（業務の書き込みと記録を混ぜない）。書けなければ例外（500） |
| 呼ぶ側 | 担当範囲の判定は業務のサービスが行い（前例 `ManagedUserLookup`）、範囲の外を結果で返す。状態を変える POST では、controller が入力の解釈より先に照会のサービスで範囲を確かめ、範囲の外ならその場で記録して 404 にする（どの controller も同じ書き方。コマンドのサービスが返す範囲の外は、その間に割り当てが変わったときにだけ通る）。画面の層のコンテキストごとに 1 つの部品（38f の見積りの側は `quotation.interfaces.web.StaffOutOfScopeResponder`、38f2 の予約の側は `booking.interfaces.web.BookingOutOfScopeResponder`、38g の経路設計の側は `routing.interfaces.web.RoutingOutOfScopeResponder`、追跡の側は `tracking.interfaces.web.TrackingOutOfScopeResponder`。どれも `@Transactional` なし）だけがポートを呼び、404 を返す。予約の確定の POST では、controller が役割の後・予約済みの判定と確定用の照会の前に、ロックしない照会（操作者を渡した見積りの公開 API）で範囲を確かめる。記録するのは状態を変える POST（輸送要求の受付の判断、見積りの作成・再見積り・提示、予約の確定、経路設計の候補の算出と確定、マイルストーンの登録）で、GET の範囲の外は 404 だけ（SEC-12。38g で「確定の POST だけ」を実態に合わせて改めた） |
| 守り | ArchUnit で、記録の工場を呼ぶのは記録のサービスだけ、ポートを呼ぶのは画面の部品だけ（トランザクションなし）、ドメインイベントと公開 API の型はポートに依存しない、共有カーネルの振る舞いの型はこのポートだけ、を確かめる。画面の部品は名前の規則（`..interfaces.web..` の `*OutOfScopeResponder`）で選び、名前の規則に合うクラスはトップレベルで `@Component` を付けた 4 つの FQN のどれかに限る閉じる規則を置く。部品ごとに実在の見張りを 1 本ずつ置く（38g） |

## 代替案

| 案 | 取らなかった理由 |
| :--- | :--- |
| 業務のコンテキストが identity の公開 API を呼ぶ | identity → `quotation :: events` の依存があるので循環する |
| 範囲の外の試行をドメインイベントで identity に渡す | 同じ request の中で同期に書く約束（IA-INV-08）を崩す。発行の記録が業務の書き込みと同じトランザクションに乗る |
| 使う側の各コンテキストがポートを持ち、identity がそれぞれを実装する | identity が業務のコンテキストの内側の型に依存し、コンテキストを足すたびに identity が変わる。共有カーネルの 1 つのポートより依存が増える |
| 業務のコンテキストが自分の監査の表を持つ | 監査記録が 2 か所に分かれ、監査人の照会（US-20）を 1 か所にできない |
| `platform :: api` にポートを置く（業務のすべてのコンテキストが `platform :: web` に依存しているので、新しい名前付きインターフェースを開く） | `platform` は技術の部品だけを置き、コンテキストの型に依存しない（`LayerArchitectureTest`）。担当範囲の外の試行は業務の意味（担当企業・監査）を持つので、技術の部品の置き場所に合わない |
| 画面の部品を 1 つにして共有する（38g） | 部品はコンテキストの画面の層に置くので、ほかのコンテキストの `interfaces.web` に依存できない。共有カーネルに置くと画面の型（404 の例外）が入る。写しは 4 つで、中身は「ポートを 1 回呼び 404 を返す」だけなので、名前の規則と閉じる規則で数と形を守る |
| ArchUnit を名前の規則だけにする（38g） | 名前さえ合えば部品が増やせ、一覧が開いたままになる。閉じる規則で 4 つの FQN に限り、足すときは規則の変更として人に諮る |

## 影響

### ポジティブ

- 依存の循環なしに、業務のコンテキストから同期に記録できる。38f2 の予約の側は同じポートを使い、38g の経路設計・追跡の側も同じポートを使った

### ネガティブ

- 共有カーネルに振る舞いの型が 1 つ入る。例外が広がらないよう ArchUnit で 1 つに限る
- 呼ぶ側の部品が外側のトランザクションの中から呼ぶと 500 になる（ArchUnit と統合テストで防ぐ）
- 型の依存の向きは保たれる（quotation は `shared` だけに依存する）が、実行時には quotation が identity の Bean（アダプター）を要する。identity を外したアプリは起動しない
- 共有カーネルの `package-info`（`shared`・`shared.domain`）の「値だけを置く」の説明を、このポートの例外を含む形に改めた

## コンプライアンス

- `LayerArchitectureTest`・`PublicApiArchitectureTest`・`DomainEventArchitectureTest` の規則（上の「守り」）
- `OutOfScopeAttemptRecorderIntegrationTest`（全列と、外側のトランザクションの中での拒否）、`StaffAssignedScopeSecurityIntegrationTest`（確定の POST で 1 行、GET と存在しない番号では書かない）

## 改訂の経緯

- 2026-10-28: 初版（Bolt 38f）
- 2026-10-28: 開発レビューを反映した。ADR-003 との関係、`platform :: api` の案、実行時の Bean の依存、package-info の変更、呼ぶ側の前の確かめを書き足した
- 2026-10-28: Bolt 38f2 で、予約の側の呼ぶ側（`BookingOutOfScopeResponder`、確定の POST の先の確かめ）を足し、予約の側では対象の種類の値を足さないことにした（確定の対象は見積り）。部品を共通にするかは 38g の開始準備で諮る
- 2026-10-29: Bolt 38g で、経路設計・追跡の値（`ROUTING_CASE`・`TRACKING_RECORD`）と 2 つの部品を足した。記録の対象を「確定の POST だけ」から「状態を変える POST」に改め（38f の見積りの作成・再見積り・提示も記録していた実態に合わせた）、ArchUnit を名前の規則と閉じる規則にした（開始準備の決定 (3)・(6)）

## 関連ドキュメント

- [バックエンドアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)、[ドメインモデル](../../design/cargo-tracker/domain_model.md)
- 関連 ADR: [ADR-001](001-modular-monolith.md)（コンプライアンスの例外）、[ADR-012](012-authentication-principal-and-session.md)（担当企業の受け渡し）、[ADR-014](014-downstream-to-upstream-notification.md)（共有カーネルに業務の意味を持たせない）、[ADR-003](003-inter-context-integration.md)（:58 は共有カーネルに `DomainEvent` のインターフェースを置かないと決めた。理由は一律の発生時刻が業務の時刻と二重になることで、振る舞いの有無ではない。このポートは業務の時刻を持たないので、その理由に当たらない）
