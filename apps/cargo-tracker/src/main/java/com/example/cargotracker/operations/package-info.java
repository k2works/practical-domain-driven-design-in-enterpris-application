/**
 * 運用コンテキストではなく、業務のコンテキストを横断する運用の部品（W10-1 の Bolt 57-2。W10-1 計画 CP-3）。業務の判断とスキーマを持たない。
 * 依存してよいのは共有カーネルだけで、ほかのモジュールはここに依存しない。業務のコンテキストの一覧には入れない。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "運用",
        allowedDependencies = {"shared"})
package com.example.cargotracker.operations;
