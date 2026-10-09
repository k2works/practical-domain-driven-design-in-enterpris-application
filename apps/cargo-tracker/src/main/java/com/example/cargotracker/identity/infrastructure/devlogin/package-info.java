/**
 * 開発用の利用者の守り（守りの 2 層目。2026-10-06 の人の決定、ADR-011 の決定 3、ADR-012）。dev の外で開発用の利用者の設定や
 * db/dev-data が使われたら、マイグレーションの前に起動を止める。ログインの画面の入力済み（守りの 1 層目）はインターフェース層の
 * DevLoginPrefill が持つ（2026-10-09 にここから infrastructure の下へ移した）。
 */
package com.example.cargotracker.identity.infrastructure.devlogin;
