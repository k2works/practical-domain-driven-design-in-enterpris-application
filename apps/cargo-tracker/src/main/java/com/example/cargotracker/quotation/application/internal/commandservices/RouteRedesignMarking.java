package com.example.cargotracker.quotation.application.internal.commandservices;

/**
 * 再設計要の印を付けた結果の数（Bolt 37e）。
 *
 * @param found その経路版を割り当てた見積りの数（失効・置換済みを含む）
 * @param newlyMarked 新しく付けた見積りの数
 */
public record RouteRedesignMarking(int found, int newlyMarked) {}
