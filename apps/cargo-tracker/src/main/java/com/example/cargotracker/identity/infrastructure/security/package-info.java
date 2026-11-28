/**
 * 認証の入口。Spring Security の利用者の読み出し、認証の主体、認証のフィルター・provider、引数の解決、監査の記録、session の上限を
 * 置く（ADR-012）。層の規則 D-5 の認証の例外（identity のコマンドサービスを呼んでよい）はこのパッケージだけにかかる。暗号の部品は
 * {@code identity.infrastructure.crypto} に置く（Bolt 31d）。
 */
package com.example.cargotracker.identity.infrastructure.security;
