/**
 * 集約。外部データは集約を持たない（トランザクションスクリプト。ADR-002）ので、外部の技術のポート（外部原本の保存）と、リポジトリの
 * ポートとそれが受け渡す振る舞いのない record（情報源など。Bolt 39b）だけを置く。
 */
package com.example.cargotracker.externaldata.domain.model.aggregates;
