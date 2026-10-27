/**
 * 通知コンテキスト。利用者へ送るメール 1 通の宛先・種類・送信結果の記録と、メールの送信を扱う（U3。ADR-017）。
 * 業務の判断を持たない。依存してよいのは共有カーネルだけで、アクセス・監査（identity）に依存しない（宛先は呼ぶ側が渡す。
 * Bolt 33）。W5 は password の再設定のメールを、W6（Bolt 38b2b）は利用者の招待のメールを、Bolt 38d は TOTP の解除・登録・再登録の
 * お知らせを、identity の腐敗防止層から公開 API で受ける。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "通知",
        allowedDependencies = {"shared"})
package com.example.cargotracker.notification;
