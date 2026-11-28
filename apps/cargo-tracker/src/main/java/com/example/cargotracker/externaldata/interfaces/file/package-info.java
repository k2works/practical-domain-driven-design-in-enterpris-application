/**
 * 取込ファイル。仮の CSV（RFC 4180）を行の値の並びにし、ヘッダーを検証する。行とファイルの誤りは例外にせず値で返し、
 * 組み立て（{@link com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler}）がキーの列と値の列を検証して、
 * 取込の値を持つ値だけの取込のコマンドにして application に渡す（ADR-004 の改訂。Bolt 39。組み立ては Bolt 39b2、値の列は Bolt 39b2b）。
 */
package com.example.cargotracker.externaldata.interfaces.file;
