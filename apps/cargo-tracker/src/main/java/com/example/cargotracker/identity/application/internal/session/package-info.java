/**
 * session の属性の名前。session・request の属性の名前と、認証の入口と画面が共有する URL（Bolt 33c）。ふるまいは持たない。認証の入口
 * （{@code identity.infrastructure.security}）と画面（{@code identity.interfaces.web}）の両方から参照する（開発ガイドライン第 3 章の置き場所の
 * 外で、2026-10-15 に human:kakimomokuri が決定。認証の入口から参照してよいことは層の規則の例外にした。ADR-012 の決定 6）。
 */
package com.example.cargotracker.identity.application.internal.session;
