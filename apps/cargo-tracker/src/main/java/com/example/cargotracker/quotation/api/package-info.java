/**
 * 見積りコンテキストの公開 API（Bolt 17）。他のコンテキストは、ドメインイベントのほかにこのパッケージだけを参照できる。
 * 照会のインターフェースと戻り値の record だけを置き、戻り値は Java の標準と共有カーネルの型と文字列だけで表す
 * （見積りのドメインの型を漏らさない。バックエンドアーキテクチャ「公開 API の置き場所と形」）。
 */
@org.springframework.modulith.NamedInterface("api")
package com.example.cargotracker.quotation.api;
