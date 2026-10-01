---
type: Design
title: "cargo-tracker データモデル"
description: "cargo-tracker の概念データモデル、スキーマ分割、命名と型の規約（H2 と PostgreSQL の共通部分）、コンテキストごとの論理データモデルと ER 図、版・追記専用・冪等性・イベント配信の表現。"
tags: [design, data-model]
status: stable
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-01T23:28:04Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-01T07:48:17Z }
  - { by: human:kakimomokuri, at: 2026-10-01T09:01:37Z }
---

# cargo-tracker データモデル

## 位置づけ

本書は、[ドメインモデル](domain_model.md) の集約を永続化する構造を決める（ドメインモデルの引継ぎ DM-01）。前提は次のとおり。

| 前提 | 出典 | データモデルへの影響 |
| :--- | :--- | :--- |
| DB は 1 つ、コンテキストごとにスキーマを分ける | ADR-001 | 7 つの業務スキーマと、基盤用のスキーマを置く |
| 集約は状態保存。版は不変、追跡の主要実績と監査記録は追記専用 | ADR-002 | 版はヘッダ・明細の形で別の行にする。追記専用の表には削除の権限を与えない |
| 冪等コマンド、永続化したドメインイベント、予約サガ | ADR-003 | 処理済みコマンド、イベント発行記録、サガ状態の表を持つ |
| 外部原本は Inbox・隔離・照合 | ADR-004 | 原本は S3 に置き、DB には冪等性キーとハッシュと分類を持つ |
| 開発は H2、本番は PostgreSQL 18、永続化は MyBatis、スキーマは Flyway | ADR-007 | DDL は H2 と PostgreSQL の共通部分で書く |

## 概念データモデル

業務の言葉で、管理する対象と関係を示す。コンテキストの境界をまたぐ関係は、ID による参照だけで表す（外部キーを張らない）。

```plantuml
@startuml
title 概念データモデル - cargo-tracker MVP
left to right direction
skinparam linetype ortho

package "見積り" {
  entity 輸送要求
  entity 輸送要求版
  entity 審査記録
  entity 見積り
  entity 料金明細
}
package "経路設計" {
  entity 経路設計案件
  entity 経路版
  entity 経路候補
  entity 区間
  entity 航海
  entity 接続時間規則
}
package "予約" {
  entity 貨物予約
  entity 予約版
  entity 変更取消申請
  entity 予約サガ
}
package "追跡" {
  entity 追跡記録
  entity 予定区間
  entity 主要実績
  entity 訂正
  entity 有人案件
}
package "アクセス・監査" {
  entity 企業
  entity 利用者
  entity 参照許可
  entity 監査記録
}
package "外部データ" {
  entity 情報源
  entity 取込
  entity 外部原本
  entity 受信記録
  entity 採用値
  entity 手動情報
  entity 復旧照合
}

輸送要求 ||--|{ 輸送要求版
輸送要求 ||--o{ 審査記録
輸送要求版 ||--o{ 見積り
見積り ||--|{ 料金明細
輸送要求版 ||..o{ 経路設計案件 : ID 参照
経路設計案件 ||--o{ 経路版
経路版 ||--|{ 経路候補
経路候補 ||--|{ 区間
区間 }o..|| 航海 : 航海番号
貨物予約 ||--|{ 予約版
貨物予約 ||--o{ 変更取消申請
貨物予約 ||--o| 予約サガ
予約版 }o..|| 見積り : ID 参照
予約版 }o..|| 経路版 : ID 参照
貨物予約 ||..|| 追跡記録 : 追跡番号
追跡記録 ||--|{ 予定区間
追跡記録 ||--o{ 主要実績
主要実績 ||--o{ 訂正
追跡記録 ||..o{ 有人案件 : 追跡番号
企業 ||--o{ 利用者
貨物予約 ||..o{ 参照許可 : 予約 ID
情報源 ||--o{ 取込
取込 ||--o{ 受信記録
外部原本 ||--|{ 受信記録
受信記録 ||--o| 採用値
情報源 ||--o{ 手動情報
復旧照合 }o--|| 情報源
@enduml
```

## スキーマ

| スキーマ | 所有するコンテキスト | 内容 |
| :--- | :--- | :--- |
| `quotation` | 見積り | 輸送要求、輸送要求の下書き、輸送要求版、審査記録、見積り、料金明細、処理済みコマンド |
| `routing` | 経路設計 | 経路設計案件、経路版、経路候補、区間、除外理由、参照情報版、航海、寄港、接続時間規則、処理済みコマンド |
| `booking` | 予約 | 貨物予約、予約版、変更取消申請、予約サガ、処理済みコマンド |
| `tracking` | 追跡 | 追跡記録、予定区間、主要実績、訂正、有人案件とその進捗・回答、照会記録、営業日カレンダー、処理済みコマンド |
| `identity` | アクセス・監査 | 企業、利用者、役割、担当範囲、参照許可、監査記録、KPI 計測記録、KPI 基準値 |
| `external_data` | 外部データ | 情報源、取込、外部原本、受信記録、採用値、手動情報、停止期間、復旧照合、差異 |
| `notification` | 通知 | 通知（送信の記録） |
| `platform` | 基盤（コンテキストに属さない） | Spring Modulith のイベント発行記録、Spring Session の表、ShedLock の表 |

`platform` スキーマはフレームワークが定める表だけを置き、業務の表を置かない。イベント発行記録はすべてのコンテキストが書き込むが、内容はフレームワークが管理し、業務のコードは直接読み書きしない。

## 命名と型の規約

ADR-007 により、DDL は H2（PostgreSQL 互換モード）と PostgreSQL 18 の両方で動く範囲で書く。

| 項目 | 規約 | 理由 |
| :--- | :--- | :--- |
| 物理名 | 英語の snake_case。表と列の日本語名は本書と DDL のコメントに書く | ドメインモデルの英語名（コード名）と揃え、H2 と PostgreSQL の両方で引用符なしで扱えるようにする |
| 主キー | 集約ルートはアプリケーションが発行する UUID（`UUID` 型）。業務キーがあるもの（航海番号、追跡番号、受付番号）は業務キーを主キーにする | 第 2 章「集約識別子はビジネスキーを使う」。DB の採番機能（シーケンスの方言差）に依存しない |
| 子の表の主キー | 親の主キー + 連番の複合主キー（例: 輸送要求 ID + 版番号） | 版・明細は親の中での順序に意味がある（ガイドのヘッダ・明細パターン） |
| 時刻 | `TIMESTAMP WITH TIME ZONE`。値は UTC で保存する | BR-10。`TIMESTAMPTZ` の略記は H2 で使えないため、DDL では正式名で書く |
| 文字列 | `VARCHAR(n)`。長い自由記述は `VARCHAR(4000)` | `TEXT` は H2 では CLOB になり、比較や索引の扱いが PostgreSQL と変わる |
| 金額 | `NUMERIC(15,2)` と通貨コード `CHAR(3)` | 浮動小数を使わない |
| 区分値 | `VARCHAR(30)` に英語の定数名（例: `UNDER_REVIEW`）を入れ、`CHECK` 制約で値を限る | 列挙型（`CREATE TYPE`）は H2 と PostgreSQL で構文が違う |
| 真偽 | `BOOLEAN` | 両方で使える |
| 使わないもの | `JSONB`、部分インデックス、`CREATE TYPE`、シーケンスの方言、トリガー | H2 で動かない。必要になったら ADR-007 に従って別の ADR で判断する |
| 楽観ロック | 集約ルートの表に `version BIGINT NOT NULL` を置き、`UPDATE ... WHERE id = ? AND version = ?` で更新する | ARCH-HO-01 の期待版の照合 |
| 監査用の列 | 集約ルートの表に `created_at`、`created_by`、`updated_at`、`updated_by` を置く | ガイドの共通設計原則。業務上の証跡は監査記録が持つ |

## 版・追記専用・冪等性の表し方

### 版（ADR-002）

版を持つ集約は、ヘッダ（集約ルート）と版の表に分ける。

| 表 | 役割 | 更新 |
| :--- | :--- | :--- |
| ヘッダ（例: `transport_request`） | 現在の状態、現在の版番号、楽観ロックの `version` | 状態の変化のたびに UPDATE する |
| 版（例: `transport_request_version`） | 版ごとの内容。主キーは（ヘッダ ID、版番号） | INSERT だけ。作った版の行は更新しない |

集約の楽観ロック（`version`）と業務上の版番号（`*_version_no`）は別の列にする。前者は同時更新の検出、後者は業務が参照する版である。

### 追記専用

| 表 | 許す操作 | 守り方 |
| :--- | :--- | :--- |
| `identity.audit_record`、`identity.kpi_baseline` | INSERT、SELECT | PostgreSQL ではアプリケーションの DB 利用者から UPDATE・DELETE の権限を外す |
| `quotation.transport_request_version`、`booking.booking_version`、`external_data.receipt` | INSERT、SELECT | UPDATE・DELETE の権限を外す |
| `tracking.milestone` | INSERT、SELECT、状態と下書き内容の UPDATE | DELETE の権限を外す。採用済みの内容を変えないことはドメインと PostgreSQL の統合テストで確かめる（T-INV-01、T-INV-07） |
| `routing.route_version` | INSERT、SELECT、UPDATE | 状態が変わる（確定 → 再設計要 → 旧版）ため権限では守らない。確定した経路版の内容（候補・区間・判断根拠・承認）を変えないことは、ドメインと PostgreSQL の統合テストで確かめる（R-INV-06） |

輸送要求の下書きは、版の表とは別の `transport_request_draft` に置く（下書きは何度も更新するため）。提出したときに、下書きの内容を `transport_request_version` に新しい版として INSERT する。これにより版の表は INSERT だけになる（Q-INV-03）。

権限の付与と剥奪は、PostgreSQL 用の Flyway のコールバック（`afterMigrate`）で、表の作成と同じ配備の中で行う（ADR-007 の補足の決定）。開発の H2 では権限による保護は働かない。保護は PostgreSQL の統合テストで確かめ、Testcontainers でも同じコールバックが動く。

### 冪等性（ARCH-HO-01）

各業務スキーマに処理済みコマンドの表を置く。

```text
<schema>.processed_command
  command_id      UUID          PK
  command_type    VARCHAR(100)  NOT NULL
  payload_hash    VARCHAR(64)   NOT NULL   -- 同じ command_id で内容が違えば衝突として拒否する
  result_ref      VARCHAR(200)              -- 例: 予約 ID と追跡番号
  processed_at    TIMESTAMP WITH TIME ZONE NOT NULL
```

コマンドの処理と同じトランザクションで INSERT する。主キーの一意制約により、同時に送られた同じコマンドの片方は失敗し、既存の結果を返す。

## 論理データモデル

列の表記は「物理名（日本語名）: 型」。`*` は NOT NULL、`PK` は主キー、`FK` は同じスキーマ内の外部キー、`UK` は一意制約、`REF` は他スキーマへの ID 参照（外部キーを張らない）を表す。

### 見積り（`quotation`）

```plantuml
@startuml
title quotation スキーマ
hide circle
skinparam linetype ortho

entity "transport_request\n輸送要求" as tr {
  * id : UUID <<PK>>
  --
  * shipper_company_id : UUID <<REF identity>>
  * status : VARCHAR(30)
  * current_version_no : INTEGER
  * version : BIGINT
  * created_at / created_by
  * updated_at / updated_by
}
entity "transport_request_version\n輸送要求版" as trv {
  * transport_request_id : UUID <<PK,FK>>
  * version_no : INTEGER <<PK>>
  --
  * consignee_company_id : UUID <<REF identity>>
  * origin_unlocode : CHAR(5)
  * destination_unlocode : CHAR(5)
  * arrival_deadline : TIMESTAMPTZ
  * cargo_category : VARCHAR(30)
  * package_type : VARCHAR(30)
  * package_count : INTEGER
  * gross_weight_kg : NUMERIC(12,3)
  * volume_m3 : NUMERIC(12,3)
  * submitted_by : UUID
  * submitted_at : TIMESTAMPTZ
}
entity "transport_request_draft\n輸送要求の下書き" as trd {
  * transport_request_id : UUID <<PK,FK>>
  --
  consignee_company_id : UUID
  origin_unlocode : CHAR(5)
  destination_unlocode : CHAR(5)
  arrival_deadline : TIMESTAMPTZ
  cargo_category : VARCHAR(30)
  package_type : VARCHAR(30)
  package_count : INTEGER
  gross_weight_kg : NUMERIC(12,3)
  volume_m3 : NUMERIC(12,3)
  copied_from_request_id : UUID
  * updated_at / updated_by
}
entity "required_document\n必要書類" as doc {
  * transport_request_id : UUID <<PK,FK>>
  * version_no : INTEGER <<PK,FK>>
  * document_no : INTEGER <<PK>>
  --
  * document_type : VARCHAR(30)
  * object_key : VARCHAR(500)
}
entity "review_record\n審査記録" as rv {
  * id : UUID <<PK>>
  --
  * transport_request_id : UUID <<FK>>
  * version_no : INTEGER
  * decision : VARCHAR(30)
  * reviewer_id : UUID
  * rationale : VARCHAR(4000)
  missing_items : VARCHAR(4000)
  * decided_at : TIMESTAMPTZ
}
entity "quotation\n見積り" as q {
  * id : UUID <<PK>>
  --
  * transport_request_id : UUID <<FK>>
  * transport_request_version_no : INTEGER
  * status : VARCHAR(30)
  * expires_at : TIMESTAMPTZ
  * total_amount : NUMERIC(15,2)
  * currency : CHAR(3)
  * route_policy_via : VARCHAR(200)
  route_policy_departure_at : TIMESTAMPTZ
  route_policy_arrival_at : TIMESTAMPTZ
  internal_approved_by : UUID
  internal_approved_at : TIMESTAMPTZ
  presented_at : TIMESTAMPTZ
  shipper_response : VARCHAR(30)
  responded_by : UUID
  responded_at : TIMESTAMPTZ
  decline_reason : VARCHAR(4000)
  routing_case_id : UUID <<REF routing>>
  route_version_no : INTEGER
  shipper_approved_by : UUID
  shipper_approved_at : TIMESTAMPTZ
  replaced_by_quotation_id : UUID <<FK>>
  * version : BIGINT
  * created_at / created_by
  * updated_at / updated_by
}
entity "pricing_line\n料金明細" as pl {
  * quotation_id : UUID <<PK,FK>>
  * line_no : INTEGER <<PK>>
  --
  * description : VARCHAR(200)
  * amount : NUMERIC(15,2)
  * currency : CHAR(3)
  contract_reference : VARCHAR(200)
}
tr ||--|{ trv
tr ||--o| trd
trv ||--o{ doc
tr ||--o{ rv
trv ||--o{ q
q ||--|{ pl
q |o--o| q : 置換
@enduml
```

| 表 | 主な制約 | 対応する不変条件 |
| :--- | :--- | :--- |
| `transport_request` | `status` IN（`DRAFT`、`UNDER_REVIEW`、`QUOTING`、`QUOTED`、`ROUTING`、`AWAITING_APPROVAL`、`READY_TO_BOOK`、`BOOKED`、`WITHDRAWN`） | 輸送要求の状態遷移 |
| `transport_request`（業務番号） | 注（設計への反映が必要）: 業務番号 `TR-年-年ごとの連番`（2026-10-02 の D-4）の列と、年ごとの採番の仕組みは、US-01 AC2 の Bolt で設計して足す。一意制約を付け、画面と通知には業務番号だけを出す | D-4、US-01 |
| `transport_request_draft` | 輸送要求ごとに 1 行。提出時に内容を版の表へ INSERT する。`copied_from_request_id` は複製元 | Q-INV-03、Q-INV-11 |
| `transport_request_version` | `cargo_category` IN（`GENERAL`、`DANGEROUS`、`REEFER`、`OTHER_SPECIAL`）。提出した版だけを INSERT し、更新しない。`submitted_at` は KPI-01 の開始時刻 | Q-INV-02、Q-INV-03、US-21 |
| `review_record` | （`transport_request_id`、`version_no`）→ `transport_request_version` の FK | Q-INV-04 |
| `quotation` | `status` IN（`DRAFT`、`PENDING_APPROVAL`、`PRESENTED`、`ROUTING_REQUESTED`、`AWAITING_SHIPPER_APPROVAL`、`APPROVED`、`EXPIRED`、`REPLACED`）。`shipper_response` IN（`PROCEED`、`DECLINED`）。`presented_at` は KPI-01 の終了時刻。荷主の承認時は `routing_case_id`・`route_version_no` が NOT NULL（アプリケーションで検証） | Q-INV-05〜10、US-21 |

見積りの「失効」は、有効期限を過ぎたときに状態を書き換えるのではなく、判定時刻と `expires_at` の比較で決める（Q-INV-06）。`status` の `EXPIRED` は、期限切れを利用者が確認した・定期処理が記録した結果として残す。予約確定の判定は常に `expires_at` で行う。

### 経路設計（`routing`）

```plantuml
@startuml
title routing スキーマ
hide circle
skinparam linetype ortho

entity "routing_case\n経路設計案件" as rc {
  * id : UUID <<PK>>
  --
  * transport_request_id : UUID <<REF quotation>>
  * transport_request_version_no : INTEGER
  * quotation_id : UUID <<REF quotation>>
  route_policy_via : VARCHAR(200)
  * origin_unlocode : CHAR(5)
  * destination_unlocode : CHAR(5)
  * arrival_deadline : TIMESTAMPTZ
  * cargo_category : VARCHAR(30)
  confirmed_route_version_no : INTEGER
  * version : BIGINT
  * created_at / created_by
  * updated_at / updated_by
}
entity "route_version\n経路版" as rver {
  * routing_case_id : UUID <<PK,FK>>
  * route_version_no : INTEGER <<PK>>
  --
  * status : VARCHAR(30)
  previous_route_version_no : INTEGER
  redesign_cause : VARCHAR(4000)
  rationale : VARCHAR(4000)
  decided_by : UUID
  approved_by : UUID
  approved_at : TIMESTAMPTZ
  selected_candidate_no : INTEGER
  * created_at : TIMESTAMPTZ
}
entity "route_candidate\n経路候補" as cand {
  * routing_case_id : UUID <<PK,FK>>
  * route_version_no : INTEGER <<PK,FK>>
  * candidate_no : INTEGER <<PK>>
  --
  * conforming : BOOLEAN
  * estimated_arrival_at : TIMESTAMPTZ
  * evaluated_at : TIMESTAMPTZ
  oldest_info_acquired_at : TIMESTAMPTZ
  * info_insufficient : BOOLEAN
}
entity "candidate_leg\n区間" as leg {
  * routing_case_id : UUID <<PK,FK>>
  * route_version_no : INTEGER <<PK,FK>>
  * candidate_no : INTEGER <<PK,FK>>
  * leg_no : INTEGER <<PK>>
  --
  * voyage_number : VARCHAR(30)
  * load_unlocode : CHAR(5)
  * discharge_unlocode : CHAR(5)
  * departure_at : TIMESTAMPTZ
  * arrival_at : TIMESTAMPTZ
  cargo_cutoff_at : TIMESTAMPTZ
  doc_cutoff_at : TIMESTAMPTZ
  * executed : BOOLEAN
}
entity "exclusion_reason\n除外理由" as ex {
  * routing_case_id : UUID <<PK,FK>>
  * route_version_no : INTEGER <<PK,FK>>
  * candidate_no : INTEGER <<PK,FK>>
  * reason_no : INTEGER <<PK>>
  --
  * reason_code : VARCHAR(30)
  violated_at : TIMESTAMPTZ
  threshold : VARCHAR(100)
  info_version : VARCHAR(100)
}
entity "referenced_info_version\n参照情報版" as riv {
  * routing_case_id : UUID <<PK,FK>>
  * route_version_no : INTEGER <<PK,FK>>
  * voyage_number : VARCHAR(30) <<PK>>
  --
  * info_version : VARCHAR(100)
  * acquired_at : TIMESTAMPTZ
}
entity "voyage\n航海" as v {
  * voyage_number : VARCHAR(30) <<PK>>
  --
  * adopted_info_version : VARCHAR(100)
  * source_kind : VARCHAR(30)
  * source_ref : VARCHAR(200)
  * acquired_at : TIMESTAMPTZ
  * version : BIGINT
  * updated_at : TIMESTAMPTZ
}
entity "port_call\n寄港" as pc {
  * voyage_number : VARCHAR(30) <<PK,FK>>
  * call_no : INTEGER <<PK>>
  --
  * port_unlocode : CHAR(5)
  arrival_at : TIMESTAMPTZ
  departure_at : TIMESTAMPTZ
}
entity "connection_rule\n接続時間規則" as cr {
  * id : UUID <<PK>>
  --
  * route_scope : VARCHAR(100)
  * port_unlocode : CHAR(5)
  * min_connection_minutes : INTEGER
  * valid_from : TIMESTAMPTZ
  valid_to : TIMESTAMPTZ
  * version : BIGINT
}
rc ||--o{ rver
rver ||--|{ cand
cand ||--|{ leg
cand ||--o{ ex
rver ||--o{ riv
v ||--|{ pc
leg }o..|| v : 航海番号
riv }o..|| v : 航海番号
@enduml
```

| 表 | 主な制約 | 対応する不変条件 |
| :--- | :--- | :--- |
| `routing_case` | UK（`transport_request_id`、`transport_request_version_no`）で、詳細経路設計の依頼（DE-16）が再配信されても案件を重複させない。`confirmed_route_version_no` は確定した経路版の番号を 1 つだけ持つ（NULL は未確定） | R-INV-05、R-INV-10 |
| `route_version` | `status` IN（`DRAFT`、`CANDIDATES_PRESENTED`、`EXPERT_REVIEW`、`CONFIRMED`、`REDESIGN_REQUIRED`、`SUPERSEDED`）。確定時は `approved_by`・`approved_at`・`rationale` が NOT NULL（アプリケーションで検証） | R-INV-03、R-INV-04、R-INV-06 |
| `exclusion_reason` | `reason_code` IN（`DEADLINE_EXCEEDED`、`CONNECTION_TOO_SHORT`、`CARGO_NOT_SUPPORTED`、`NOT_CONNECTABLE`、`INFO_INSUFFICIENT`） | R-INV-02 |
| `referenced_info_version` | 確定時に参照した航海ごとの情報版。承認時の再検証と、確定後の再評価に使う | R-INV-03、R-INV-07 |
| `candidate_leg` | `executed` は再設計の対象から外す区間を示す | R-INV-08 |

「確定は案件に 1 つ」は PostgreSQL の部分一意インデックスで表せるが、H2 で使えないため、案件のヘッダに `confirmed_route_version_no` を 1 つだけ持つことで表す（命名と型の規約）。

### 予約（`booking`）

```plantuml
@startuml
title booking スキーマ
hide circle
skinparam linetype ortho

entity "booking\n貨物予約" as b {
  * id : UUID <<PK>>
  --
  * tracking_number : VARCHAR(20) <<UK>>
  * shipper_company_id : UUID <<REF identity>>
  * status : VARCHAR(30)
  * transport_phase : VARCHAR(30)
  * current_version_no : INTEGER
  * version : BIGINT
  * created_at / created_by
  * updated_at / updated_by
}
entity "booking_version\n予約版" as bv {
  * booking_id : UUID <<PK,FK>>
  * booking_version_no : INTEGER <<PK>>
  --
  * transport_request_id : UUID <<REF quotation>>
  * transport_request_version_no : INTEGER
  * quotation_id : UUID <<REF quotation>>
  * routing_case_id : UUID <<REF routing>>
  * route_version_no : INTEGER
  * consignee_company_id : UUID <<REF identity>>
  * cargo_category : VARCHAR(30)
  * cargo_summary : VARCHAR(1000)
  * shipper_approver_id : UUID
  * confirmed_by : UUID
  * committed_at : TIMESTAMPTZ
}
entity "amendment_request\n変更取消申請" as ar {
  * id : UUID <<PK>>
  --
  * booking_id : UUID <<FK>>
  * kind : VARCHAR(30)
  * status : VARCHAR(30)
  * requested_by : UUID
  * requested_at : TIMESTAMPTZ
  * reason : VARCHAR(4000)
  decided_by : UUID
  decided_at : TIMESTAMPTZ
  decision_note : VARCHAR(4000)
  service_case_number : VARCHAR(20)
}
entity "booking_saga\n予約サガ" as saga {
  * id : UUID <<PK>>
  --
  * booking_id : UUID <<FK, UK>>
  * tracking_number : VARCHAR(20)
  * status : VARCHAR(30)
  * current_step : VARCHAR(50)
  * attempts : INTEGER
  next_retry_at : TIMESTAMPTZ
  last_error : VARCHAR(4000)
  service_case_number : VARCHAR(20)
  * started_at : TIMESTAMPTZ
  * updated_at : TIMESTAMPTZ
  * version : BIGINT
}
b ||--|{ bv
b ||--o{ ar
b ||--o| saga
@enduml
```

| 表 | 主な制約 | 対応する不変条件 |
| :--- | :--- | :--- |
| `booking` | `tracking_number` は一意。`status` IN（`CONFIRMED`、`AMENDMENT_PENDING`、`CANCELLATION_PENDING`、`AMENDING`、`CANCELLED`、`IN_TRANSIT`、`COMPLETED`）。`transport_phase` IN（`BEFORE_PICKUP`、`AFTER_PICKUP`、`COMPLETED`） | B-INV-06、B-INV-07 |
| `booking_version` | 予約確定時の見積り・経路版・荷受人・貨物の写しと、確定者・commit 時刻。UK（`quotation_id`）で、1 つの見積りから 2 件目の予約を作れない（別のコマンド ID による同時確定でも片方が一意制約で失敗する） | B-INV-01、B-INV-08、B-INV-11 |
| `booking_saga` | 予約ごとに 1 つ。`status` IN（`IN_PROGRESS`、`COMPLETED`、`FAILED`、`NEEDS_HUMAN`） | ARCH-HO-02 |
| `processed_command` | 本予約確定のコマンド ID を記録し、再送には既存の予約 ID と追跡番号を返す | B-INV-03 |

`booking_version.cargo_summary` は予約確定時の貨物の写しで、見積りの内部情報（料金明細）は複製しない（units.md「見積り内部情報を予約へ複製しない」）。

### 追跡（`tracking`）

```plantuml
@startuml
title tracking スキーマ
hide circle
skinparam linetype ortho

entity "tracking_record\n追跡記録" as trk {
  * tracking_number : VARCHAR(20) <<PK>>
  --
  * booking_id : UUID <<REF booking>>
  * shipper_company_id : UUID <<REF identity>>
  * consignee_company_id : UUID <<REF identity>>
  * booking_status : VARCHAR(30)
  routing_case_id : UUID <<REF routing>>
  route_version_no : INTEGER
  * current_status : VARCHAR(30)
  status_basis_milestone_no : INTEGER
  under_review_reason : VARCHAR(1000)
  procedure_stage : VARCHAR(30)
  original_eta : TIMESTAMPTZ
  latest_eta : TIMESTAMPTZ
  last_acquired_at : TIMESTAMPTZ
  * version : BIGINT
  * created_at : TIMESTAMPTZ
  * updated_at : TIMESTAMPTZ
}
entity "scheduled_leg\n予定区間" as sl {
  * tracking_number : VARCHAR(20) <<PK,FK>>
  * leg_no : INTEGER <<PK>>
  --
  * voyage_number : VARCHAR(30)
  * load_unlocode : CHAR(5)
  * discharge_unlocode : CHAR(5)
  * departure_at : TIMESTAMPTZ
  * arrival_at : TIMESTAMPTZ
}
entity "milestone\n主要実績" as m {
  * tracking_number : VARCHAR(20) <<PK,FK>>
  * milestone_no : INTEGER <<PK>>
  --
  * kind : VARCHAR(30)
  * location_unlocode : CHAR(5)
  * occurred_at : TIMESTAMPTZ
  * source_kind : VARCHAR(30)
  * source_ref : VARCHAR(200)
  * acquired_at : TIMESTAMPTZ
  * state : VARCHAR(30)
  * shown_to_customer : BOOLEAN
  * registered_by : UUID
  * registered_at : TIMESTAMPTZ
  revised_by : UUID
  revised_at : TIMESTAMPTZ
}
entity "correction\n訂正" as c {
  * id : UUID <<PK>>
  --
  * tracking_number : VARCHAR(20) <<FK>>
  * target_milestone_no : INTEGER <<FK>>
  * new_kind : VARCHAR(30)
  * new_location_unlocode : CHAR(5)
  * new_occurred_at : TIMESTAMPTZ
  * reason : VARCHAR(4000)
  * status : VARCHAR(30)
  * registered_by : UUID
  * registered_at : TIMESTAMPTZ
  approved_by : UUID
  approved_at : TIMESTAMPTZ
}
entity "service_case\n有人案件" as sc {
  * case_number : VARCHAR(20) <<PK>>
  --
  * kind : VARCHAR(30)
  shipper_company_id : UUID <<REF identity>>
  booking_id : UUID <<REF booking>>
  tracking_number : VARCHAR(20)
  * cause : VARCHAR(4000)
  * priority : VARCHAR(30)
  * department : VARCHAR(50)
  assignee_id : UUID
  received_at : TIMESTAMPTZ
  * receive_due_at : TIMESTAMPTZ
  answer_due_at : TIMESTAMPTZ
  next_update_at : TIMESTAMPTZ
  old_version_ref : VARCHAR(200)
  new_version_ref : VARCHAR(200)
  manual_source_ref : VARCHAR(200)
  * reconciliation_required : BOOLEAN
  reconciliation_id : UUID <<REF external_data>>
  acknowledged_at : TIMESTAMPTZ
  * status : VARCHAR(30)
  * version : BIGINT
  * created_at / created_by
  * updated_at / updated_by
}
entity "service_case_source\n参照した出典" as scs {
  * case_number : VARCHAR(20) <<PK,FK>>
  * source_no : INTEGER <<PK>>
  --
  * source_kind : VARCHAR(30)
  * source_ref : VARCHAR(200)
}
entity "service_case_progress\n進捗" as scp {
  * case_number : VARCHAR(20) <<PK,FK>>
  * progress_no : INTEGER <<PK>>
  --
  * content : VARCHAR(4000)
  next_update_at : TIMESTAMPTZ
  * updated_by : UUID
  * updated_at : TIMESTAMPTZ
}
entity "service_case_answer\n回答" as sca {
  * case_number : VARCHAR(20) <<PK,FK>>
  * answer_no : INTEGER <<PK>>
  --
  * content : VARCHAR(4000)
  * answered_by : UUID
  * answered_at : TIMESTAMPTZ
  customer_response : VARCHAR(4000)
  customer_responded_at : TIMESTAMPTZ
}
entity "inquiry_record\n照会記録" as ir {
  * id : UUID <<PK>>
  --
  * channel : VARCHAR(30)
  * shipper_company_id : UUID
  tracking_number : VARCHAR(20)
  * inquirer_kind : VARCHAR(30)
  inquirer_user_id : UUID
  * received_at : TIMESTAMPTZ
  * escalated : BOOLEAN
  case_number : VARCHAR(20)
  recorded_by : UUID
}
entity "business_calendar\n営業日カレンダー" as bc {
  * calendar_date : DATE <<PK>>
  --
  * business_day : BOOLEAN
  open_time : TIME
  close_time : TIME
  * time_zone : VARCHAR(50)
  note : VARCHAR(200)
}
trk ||--|{ sl
trk ||--o{ m
m ||--o{ c
trk ||..o{ sc : 追跡番号
sc ||--o{ scs
sc ||--o{ scp
sc ||--o{ sca
@enduml
```

| 表 | 主な制約 | 対応する不変条件 |
| :--- | :--- | :--- |
| `tracking_record` | `current_status` は主要実績から導出した結果の保存（照会のため）。導出の正は集約のロジック。`original_eta`（確定した経路版の到着予定）と `latest_eta`（最新の見込み）を別に持つ。`procedure_stage` は通関等の手続き中の段階 | T-INV-08、T-INV-10 |
| `milestone` | UK（`tracking_number`、`source_kind`、`source_ref`）。`state` IN（`DRAFT`、`ADOPTED`、`UNDER_REVIEW`、`RETAINED_ONLY`）。DELETE の権限なし | T-INV-01〜03、T-INV-05 |
| `correction` | `status` IN（`PENDING_APPROVAL`、`APPLIED`、`REJECTED`）。`approved_by` は `registered_by` と異なる（`CHECK`） | T-INV-06 |
| `service_case` | `kind` IN（`CUSTOMER_INQUIRY`、`QUOTATION_CONSULTATION`、`AMENDMENT`、`REDESIGN`、`EXTERNAL_OUTAGE`、`REJECTED_MILESTONE`、`SAGA_FAILURE`）。`status` IN（`RECEIVED`、`ESCALATED`、`IN_PROGRESS`、`ANSWERED`、`CLOSED`、`DUPLICATE_CLOSED`）。`shipper_company_id` で荷主の照会範囲を限る。`acknowledged_at` は営業時間外の優先度高の自動受付の時刻 | SC-INV-01〜05 |
| `inquiry_record` | `channel` IN（`WEB_SELF`、`WEB_INQUIRY`、`PHONE`、`EMAIL`）。`inquirer_kind` IN（`SHIPPER`、`CONSIGNEE`）。`escalated` は有人対応へ移ったか | IR-INV-01〜02、KPI-02 |
| `business_calendar` | 日ごとの営業日・営業時間。年次で翌年分を登録する | SC-INV-04 |

`tracking_record.current_status` は導出結果を保存した非正規化である。照会（US-09）のたびに全実績から導出し直すのを避けるためで、集約を保存するときに必ず導出し直して書く。

`correction` の `CHECK (approved_by IS NULL OR approved_by <> registered_by)` は H2 と PostgreSQL の両方で使える。

### アクセス・監査（`identity`）

```plantuml
@startuml
title identity スキーマ
hide circle
skinparam linetype ortho

entity "company\n企業" as co {
  * id : UUID <<PK>>
  --
  * name : VARCHAR(200)
  * kind : VARCHAR(30)
  * active : BOOLEAN
  * version : BIGINT
  * created_at / updated_at
}
entity "app_user\n利用者" as u {
  * id : UUID <<PK>>
  --
  * company_id : UUID <<FK>>
  * email : VARCHAR(320) <<UK>>
  * display_name : VARCHAR(200)
  * password_hash : VARCHAR(200)
  totp_secret_encrypted : VARCHAR(500)
  * status : VARCHAR(30)
  * failed_attempts : INTEGER
  locked_until : TIMESTAMPTZ
  * version : BIGINT
  * created_at / updated_at
}
entity "user_role\n役割" as ur {
  * user_id : UUID <<PK,FK>>
  * role : VARCHAR(30) <<PK>>
  --
  * granted_by : UUID
  * granted_at : TIMESTAMPTZ
}
entity "user_assignment\n担当範囲" as ua {
  * user_id : UUID <<PK,FK>>
  * scope_kind : VARCHAR(30) <<PK>>
  * scope_id : UUID <<PK>>
  --
  * granted_by : UUID
  * granted_at : TIMESTAMPTZ
}
entity "access_grant\n参照許可" as ag {
  * id : UUID <<PK>>
  --
  * booking_id : UUID <<REF booking>>
  * consignee_company_id : UUID <<FK>>
  consignee_user_id : UUID <<FK>>
  * status : VARCHAR(30)
  * valid_until : TIMESTAMPTZ
  * invited_by : UUID
  * invited_at : TIMESTAMPTZ
  accepted_at : TIMESTAMPTZ
  revoked_by : UUID
  revoked_at : TIMESTAMPTZ
  * version : BIGINT
}
entity "audit_record\n監査記録" as au {
  * id : UUID <<PK>>
  --
  * occurred_at : TIMESTAMPTZ
  actor_user_id : UUID
  actor_company_id : UUID
  * action : VARCHAR(100)
  * result : VARCHAR(30)
  target_type : VARCHAR(50)
  target_id : VARCHAR(100)
  target_version : VARCHAR(50)
  before_state : VARCHAR(4000)
  after_state : VARCHAR(4000)
  reason : VARCHAR(4000)
  approval_ref : VARCHAR(200)
  source_ref : VARCHAR(200)
  correlation_id : VARCHAR(100)
  event_id : UUID <<UK>>
}
entity "kpi_observation\nKPI 計測記録" as ko {
  * transport_request_id : UUID <<PK>>
  --
  * shipper_company_id : UUID
  * submitted_at : TIMESTAMPTZ
  first_presented_at : TIMESTAMPTZ
  * excluded : BOOLEAN
  exclusion_reason : VARCHAR(1000)
  excluded_by : UUID
}
entity "kpi_baseline\nKPI 基準値" as kb {
  * id : UUID <<PK>>
  --
  * kpi : VARCHAR(30)
  * period_from : DATE
  * period_to : DATE
  * value_p50 : NUMERIC(12,2)
  value_p90 : NUMERIC(12,2)
  * method : VARCHAR(4000)
  * registered_by : UUID
  * registered_at : TIMESTAMPTZ
  supersedes_id : UUID
}
co ||--o{ u
u ||--o{ ur
u ||--o{ ua
co ||--o{ ag
@enduml
```

| 表 | 主な制約 | 対応する不変条件 |
| :--- | :--- | :--- |
| `user_role` | `role` IN（BR-15 の 8 役割）。システム管理者・監査担当者と業務の役割の併用は、アプリケーションで拒否する | IA-INV-01 |
| `user_assignment` | `scope_kind` IN（`COMPANY`、`BOOKING`） | IA-INV-02 |
| `app_user` | `failed_attempts` と `locked_until` で 5 回失敗・15 分ロックを表す | IA-INV-03 |
| `access_grant` | `status` IN（`INVITED`、`ACTIVE`、`REVOKED`、`EXPIRED`）。同じ予約・荷受人企業に有効な許可を重複させないことはアプリケーションで確認する | IA-INV-05、IA-INV-06 |
| `audit_record` | UPDATE・DELETE の権限なし。`event_id` の一意制約で、イベントの再配信による重複記録を防ぐ。認証の失敗と権限外のアクセス試行は `event_id` を持たず、同期で書く | IA-INV-07、IA-INV-08 |
| `kpi_observation` | 輸送要求ごとに 1 行。DE-01 で作り、DE-03 で最初の提示時刻だけを記録する（2 回目以降の提示では更新しない） | KPI-INV-01 |
| `kpi_baseline` | UPDATE・DELETE の権限なし。訂正は `supersedes_id` で前の行を指す新しい行にする | KPI-INV-02 |

session は `platform` スキーマの Spring Session の表に置く。利用停止・権限取消し・参照許可の取消しを次の request から反映するため、認可の判断は session に保存した値ではなく、request ごとに `app_user`・`user_role`・`access_grant` を確かめる（IA-INV-04、IA-INV-06）。

`audit_record.before_state` と `after_state` は変更前後の要約である。4000 文字を超える詳細が必要になったら、データモデルを見直す。

### 通知（`notification`）

```plantuml
@startuml
title notification スキーマ
hide circle
entity "notification\n通知" as n {
  * id : UUID <<PK>>
  --
  * event_id : UUID
  * recipient_user_id : UUID <<REF identity>>
  * email : VARCHAR(320)
  * kind : VARCHAR(30)
  * target_ref : VARCHAR(200)
  * locale : VARCHAR(10)
  * status : VARCHAR(30)
  sent_at : TIMESTAMPTZ
  failure_reason : VARCHAR(1000)
  * created_at : TIMESTAMPTZ
  .. UK (event_id, recipient_user_id) ..
}
@enduml
```

| 表 | 主な制約 | 対応する不変条件 |
| :--- | :--- | :--- |
| `notification` | UK（`event_id`、`recipient_user_id`）で、イベントの再配信でも同じ宛先へ 2 通送らない。`kind` IN（`QUOTATION_PRESENTED`、`ROUTE_APPROVED`、`QUOTATION_EXPIRING`、`BOOKING_CONFIRMED`、`TRACKING_UNDER_REVIEW`、`CONSIGNEE_INVITATION`、`PASSWORD_RESET`）。`status` IN（`PENDING`、`SENT`、`FAILED`） | N-INV-02、N-INV-03 |

メールの本文は保存しない（料金などを含まないことは N-INV-01 で守り、本文はテンプレートと `target_ref` から再現できる）。

### 外部データ（`external_data`）

```plantuml
@startuml
title external_data スキーマ
hide circle
skinparam linetype ortho

entity "data_source\n情報源" as ds {
  * id : UUID <<PK>>
  --
  * provider : VARCHAR(100)
  * record_type : VARCHAR(50)
  * approval_status : VARCHAR(30)
  approved_by : UUID
  approved_at : TIMESTAMPTZ
  * version : BIGINT
}
entity "ingestion\n取込" as ing {
  * id : UUID <<PK>>
  --
  * data_source_id : UUID <<FK>>
  * file_object_key : VARCHAR(500)
  * file_hash : VARCHAR(64)
  * ingested_by : UUID
  * ingested_at : TIMESTAMPTZ
  * received_count : INTEGER
}
entity "external_record\n外部原本" as er {
  * id : UUID <<PK>>
  --
  * provider : VARCHAR(100)
  * record_type : VARCHAR(50)
  * external_id : VARCHAR(200)
  * external_version : VARCHAR(100)
  * payload_hash : VARCHAR(64)
  * payload_object_key : VARCHAR(500)
  * first_received_at : TIMESTAMPTZ
  .. UK (provider, record_type, external_id, external_version) ..
}
entity "receipt\n受信記録" as rc {
  * id : UUID <<PK>>
  --
  * external_record_id : UUID <<FK>>
  ingestion_id : UUID <<FK>>
  * payload_hash : VARCHAR(64)
  * payload_object_key : VARCHAR(500)
  * acquired_at : TIMESTAMPTZ
  * classification : VARCHAR(30)
  classification_note : VARCHAR(1000)
  * classified_at : TIMESTAMPTZ
}
entity "adopted_value\n採用値" as av {
  * id : UUID <<PK>>
  --
  * receipt_id : UUID <<FK, UK>>
  * target_type : VARCHAR(30)
  * target_key : VARCHAR(100)
  * value_summary : VARCHAR(4000)
  * info_version : VARCHAR(100)
  * published_at : TIMESTAMPTZ
}
entity "outage\n停止期間" as og {
  * id : UUID <<PK>>
  --
  * data_source_id : UUID <<FK>>
  * started_at : TIMESTAMPTZ
  ended_at : TIMESTAMPTZ
}
entity "manual_entry\n手動情報" as me {
  * id : UUID <<PK>>
  --
  * data_source_id : UUID <<FK>>
  outage_id : UUID <<FK>>
  * source_ref : VARCHAR(200)
  * entered_by : UUID
  * entered_at : TIMESTAMPTZ
  * provisional : BOOLEAN
  * target_type : VARCHAR(30)
  * target_key : VARCHAR(100)
  * value_summary : VARCHAR(4000)
}
entity "recovery_reconciliation\n復旧照合" as rr {
  * id : UUID <<PK>>
  --
  * outage_id : UUID <<FK, UK>>
  * status : VARCHAR(30)
  expected_count : INTEGER
  adopted_count : INTEGER
  duplicate_count : INTEGER
  quarantined_count : INTEGER
  discrepancy_count : INTEGER
  confirmed_by : UUID
  completed_at : TIMESTAMPTZ
  * version : BIGINT
}
entity "reconciliation_discrepancy\n差異" as rd {
  * reconciliation_id : UUID <<PK,FK>>
  * discrepancy_no : INTEGER <<PK>>
  --
  manual_entry_id : UUID <<FK>>
  receipt_id : UUID <<FK>>
  * description : VARCHAR(4000)
  resolution : VARCHAR(4000)
  resolved_by : UUID
  resolved_at : TIMESTAMPTZ
}
entity "fetch_failure\n取得失敗" as ff {
  * id : UUID <<PK>>
  --
  * data_source_id : UUID <<FK>>
  * failed_at : TIMESTAMPTZ
  * reason : VARCHAR(1000)
  retry_after : TIMESTAMPTZ
}
ds ||--o{ ing
ing ||--o{ rc
er ||--|{ rc
rc ||--o| av
ds ||--o{ og
ds ||--o{ me
og ||--o{ me
og ||--o| rr
rr ||--o{ rd
ds ||--o{ ff
@enduml
```

| 表 | 主な制約 | 対応する規則 |
| :--- | :--- | :--- |
| `data_source` | `approval_status` IN（`CANDIDATE`、`APPROVED`、`SUSPENDED`）。承認済みが 0 件ならパイロットを開始しない（アプリケーションで判定） | BR-18 |
| `external_record` | 冪等性キー（提供元、原本種別、外部 ID、外部版）の一意制約。最初に受け取った payload のハッシュを持つ | BR-12 |
| `receipt` | 受け取るたびに 1 行。`classification` IN（`ADOPTED`、`DUPLICATE`、`QUARANTINED`、`DISCREPANCY`）。ハッシュが外部原本と同じなら重複、違えば隔離。UPDATE・DELETE の権限なし | BR-12、US-14 |
| `adopted_value` | 採用した受信記録ごとに 1 つ。ここから DE-12・DE-13 を発行する | ADR-004 |
| `manual_entry` | 出典・入力者・入力時刻・仮状態を NOT NULL にする | BR-08 |
| `recovery_reconciliation` | 停止期間ごとに 1 つ。完了には全件の分類、件数の一致、差異の解消が必要（アプリケーションで判定） | BR-12、US-15 |

原本の payload は S3 に置き（ADR-004、ADR-008）、DB には `payload_object_key` とハッシュだけを持つ。開発環境では同じキーでローカルのファイルシステムに置く（ADR-007）。

### 基盤（`platform`）

| 表 | 提供元 | 用途 |
| :--- | :--- | :--- |
| `event_publication`（と完了済みの保管表） | Spring Modulith（JDBC） | イベント発行記録。発行と同じトランザクションで記録し、購読の完了を記録する。未完了のものを再配信する（ADR-003） |
| `spring_session`、`spring_session_attributes` | Spring Session JDBC | 複数インスタンス間で共有する session |
| `shedlock` | ShedLock | 定期処理（再配信、予約サガの再試行、日次の処理）を 1 インスタンスに限るためのロック |

表の定義はフレームワークの提供する DDL に従う。DDL は DB ごとに違うため、Flyway の `db/migration/{vendor}/` に H2 用と PostgreSQL 用を分けて置く（ADR-007）。完了したイベント発行記録は 30 日（RET-05）で削除する。

## マイグレーションの構成

```text
src/main/resources/db/
├── migration/
│   ├── common/        業務の表（H2 と PostgreSQL の共通部分）
│   │   ├── V20261001090000__create_identity.sql
│   │   ├── V20261001090100__create_quotation.sql
│   │   └── ...
│   ├── h2/            フレームワークの表（H2 用）
│   └── postgresql/    フレームワークの表（PostgreSQL 用）
└── callback/
    └── postgresql/
        └── afterMigrate__grant_app_user.sql   権限の付与と、追記専用の表の UPDATE・DELETE の剥奪
```

| 規約 | 内容 |
| :--- | :--- |
| 版番号 | 作成日時の版番号（`VyyyyMMddHHmmss__説明.sql`）にする。コンテキストごとの版番号の帯は使わない（ADR-007。帯を分けると、後から小さい番号を足したときに検証で失敗する） |
| 1 つの Flyway | `common` と `{vendor}` の場所を 1 つの Flyway の実行で適用し、履歴表は 1 つにする |
| 共通部分 | 業務の表の DDL は H2 と PostgreSQL の両方で実行する（ADR-007）。CI で両方に適用して確かめる |
| 権限 | 権限の付与と剥奪は PostgreSQL 用の `afterMigrate` のコールバックで行い、表の作成と同じ配備で反映する。新しい表を足したら、同じ変更でコールバックも更新する |
| 後方互換 | ローリングデプロイのため、列の削除・名前の変更は「追加 → 移行 → 削除」に分ける（インフラ設計） |
| 凍結の時点 | 最初にステージングへ配置したマイグレーションは書き換えない。それまでは Bolt で使う列だけを作り、後の Bolt で足す。凍結の後に `NOT NULL` の列を足すときは、既定値付きで追加するか「追加 → 移行 → 制約」の 3 段にする。状態の `CHECK` 制約は、状態を足すたびに作り直す（Bolt 1 レビュー R-37） |

## インデックス

照会の主な経路に対してだけ置く。性能目標が決まった後（非機能要件）に見直す。

| 表 | インデックス | 使う照会 |
| :--- | :--- | :--- |
| `quotation.transport_request` | （`shipper_company_id`、`status`） | 荷主の輸送要求の一覧 |
| `quotation.quotation` | （`transport_request_id`、`status`） | 輸送要求の有効な見積り |
| `routing.routing_case` | （`transport_request_id`） | 輸送要求から経路設計案件 |
| `routing.referenced_info_version` | （`voyage_number`） | 航海の更新で再評価する確定済み経路版の検索（DE-12） |
| `booking.booking` | `tracking_number`（一意）、（`shipper_company_id`、`status`） | 追跡番号での照会、荷主の予約一覧 |
| `booking.booking_saga` | （`status`、`next_retry_at`） | 再試行の対象 |
| `tracking.tracking_record` | （`shipper_company_id`）、（`consignee_company_id`） | 荷主・荷受人の照会 |
| `tracking.service_case` | （`status`、`receive_due_at`） | 受領期限を過ぎた案件（escalation） |
| `identity.access_grant` | （`booking_id`、`consignee_company_id`、`status`） | 参照許可の確認（request ごと） |
| `identity.audit_record` | （`target_type`、`target_id`、`occurred_at`）、（`actor_user_id`、`occurred_at`） | 監査証跡の照会（US-17） |
| `external_data.receipt` | （`ingestion_id`）、（`classification`） | 取込単位の件数、隔離中の一覧 |
| `tracking.service_case` | （`shipper_company_id`、`status`） | 荷主の問い合わせ一覧（US-23） |
| `tracking.inquiry_record` | （`shipper_company_id`、`received_at`） | KPI-02 の週次集計 |
| `identity.kpi_observation` | （`submitted_at`） | KPI-01 の週次集計 |
| `quotation.quotation` | （`status`、`expires_at`） | 期限間近の見積り（DE-19）、荷主承認済み・期限間近の一覧 |

## ドメインモデルとの対応

| 集約（ドメインモデル） | 表 | 対応の注意 |
| :--- | :--- | :--- |
| 輸送要求 | `transport_request`、`transport_request_draft`、`transport_request_version`、`required_document`、`review_record` | 輸送条件（値オブジェクト）は下書きと版の表の列に展開する |
| 見積り | `quotation`、`pricing_line` | 料金根拠は明細の表、有効期限・経路方針・荷主の回答・荷主承認は列 |
| 経路設計案件 | `routing_case`、`route_version`、`route_candidate`、`candidate_leg`、`exclusion_reason`、`referenced_info_version` | 制約適合判定（値オブジェクト）は候補の列と除外理由の表に展開する |
| 航海 | `voyage`、`port_call` | — |
| 接続時間規則 | `connection_rule` | — |
| 貨物予約 | `booking`、`booking_version`、`amendment_request` | 確定条件（値オブジェクト）は保存しない。確定時に検証するだけ |
| 予約サガ | `booking_saga` | ドメインモデルの予約サガの状態 |
| 追跡記録 | `tracking_record`、`scheduled_leg`、`milestone`、`correction` | 現在状態は導出結果を保存する（非正規化） |
| 有人案件 | `service_case`、`service_case_source`、`service_case_progress`、`service_case_answer` | — |
| 照会記録・営業日カレンダー | `inquiry_record`、`business_calendar` | — |
| KPI 計測記録 | `kpi_observation`、`kpi_baseline` | — |
| 通知 | `notification` | — |
| 企業・利用者・参照許可・監査記録 | `company`、`app_user`、`user_role`、`user_assignment`、`access_grant`、`audit_record` | 認証の仕組みは Spring Security が使う |
| 外部データ（トランザクションスクリプト） | `external_data` の各表 | — |

## 後続工程への引継ぎ

| ID | 引継ぎ内容 | 引継ぎ先 |
| :--- | :--- | :--- |
| DA-01 | 監査記録・完了済みイベント発行記録・外部原本・session の保持期間と掃除の方針 | 非機能要件 |
| DA-02 | DB 利用者（マイグレーション用・アプリケーション用）の作成。権限の付与と剥奪は Flyway のコールバックで行う | 運用要件、`operating-provision` |
| DA-03 | H2 と PostgreSQL の両方へのマイグレーション適用、追記専用・CHECK 制約・一意制約のテスト | テスト戦略 |
| DA-04 | 照会の性能目標に基づくインデックスの見直し | 非機能要件 |

## AI の仮定と要確認

- **DB 利用者の分け方**: アプリケーションの DB 利用者は 1 つにし、スキーマ境界はマッパーの SQL の静的検査で守る（2026-10-01 に human:kakimomokuri が決定、ADR-001 を改訂）。追記専用の表の権限剥奪は表単位で行う。
- **荷受人**: 輸送条件に荷受人企業を加えた（UC-01 の記録項目、IA-INV-05 の判定に必要）。ドメインモデルにも同じ修正を入れた。
- **金額の通貨**: 見積りは 1 つの通貨で表すと仮定した。複数通貨の混在が必要かは営業責任者に確認する。
- **追跡番号の形式**: `VARCHAR(20)` とし、推測されにくい形式の具体は実装で決める。
- **外部原本の個人情報と保持**: 外部原本のファイルは荷受人の担当者名などの個人情報を含みうる。Object Lock（コンプライアンスモード）の 7 年の間は削除できないため、取引の証跡として保持期間の満了まで残し、満了後に PRV-06 に従って削除する。保持期間中の利用は採否の照合と監査に限る（要確認: 管理・コンプライアンス責任者）。
- **保持期間を過ぎた記録の削除**: 追記専用の表はアプリケーションの DB 利用者では削除できないため、年次の削除（`retention:purge`）はマイグレーション用の DB 利用者で、承認を得て実行する（運用要件）。
