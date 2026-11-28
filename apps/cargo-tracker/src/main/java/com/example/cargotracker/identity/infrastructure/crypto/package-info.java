/**
 * 認証の要素の暗号の部品。TOTP の秘密の暗号化（AES-GCM）、Base32、TOTP の生成、秘密の発行と QR、回復コードの生成とハッシュを置く
 * （Bolt 31d、ADR-011 の決定 8）。平文の秘密と暗号・TOTP・QR のライブラリはこのパッケージに閉じる。application にも認証の入口
 * （{@code identity.infrastructure.security}）にも依存しない（層の規則 D-5 の認証の例外は入口だけにかかる）。保存した要素を読めない
 * ときは {@code UnreadableCredentialException} を投げる。
 */
package com.example.cargotracker.identity.infrastructure.crypto;
