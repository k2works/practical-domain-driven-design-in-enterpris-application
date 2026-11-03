package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 取込の値。採用の前に行から読んだ値で、採用値（{@code AdoptedValue}）とは別（Bolt 39b2b）。航海の値か主要実績の値。
 * 取り込む行に載せ、受信記録の列には書かない（値は payload の行の原文に残る）。採用値（Bolt 39b3）の材料になる。
 */
@ValueObject
public sealed interface RecordValues permits VoyageValues, MilestoneValues {}
