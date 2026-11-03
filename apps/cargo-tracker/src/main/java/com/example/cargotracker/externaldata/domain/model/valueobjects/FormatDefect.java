package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 形式の誤り。取込のファイルと行を隔離する理由（UC-15 2a。Bolt 39b2）。解析・ヘッダーの検証・組み立てがこの値を直接持ち、application に渡す。
 * 理由の名前は Bolt 39 で決めた名前を変えない。組み立ての検証の理由（空の値・時刻の書式・長すぎる値）と注記の業務の言葉は、それを使うステップで足す。
 */
@ValueObject
public enum FormatDefect {
    /** 文字コードが UTF-8 ではない（不正なバイト列。ファイルの誤り）。 */
    UNDECODABLE,
    /** ヘッダーの行を読めない（ファイルの誤り）。 */
    UNREADABLE_HEADER,
    /** 必要な列がない（ヘッダーの行がない場合を含む。ファイルの誤り）。 */
    MISSING_COLUMN,
    /** 同じ名前の列が 2 つ以上ある（ファイルの誤り）。 */
    DUPLICATE_COLUMN,
    /** 引用符を閉じないままファイルが終わった（取込全体の要確認）。 */
    UNCLOSED_QUOTE,
    /** 閉じた引用符の後に、区切り（カンマか改行）でない文字がある（行の誤り）。 */
    TEXT_AFTER_CLOSING_QUOTE,
    /** 引用符で囲まない値の中に引用符がある（RFC 4180 では引用符を含む値は引用符で囲む。行の誤り）。 */
    BARE_QUOTE,
    /** 列数がヘッダーと違う（行の誤り）。 */
    COLUMN_COUNT_MISMATCH
}
