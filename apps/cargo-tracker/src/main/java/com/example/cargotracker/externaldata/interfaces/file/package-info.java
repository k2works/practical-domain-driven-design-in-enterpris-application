/**
 * 取込ファイル。仮の CSV（RFC 4180）を行の値の並びにし、ヘッダーを検証する。行とファイルの誤りは例外にせず値で返し、
 * application には値だけを渡す（ADR-004 の改訂。Bolt 39。渡す値の組の型は 39b で決める）。
 */
package com.example.cargotracker.externaldata.interfaces.file;
