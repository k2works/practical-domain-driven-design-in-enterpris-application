package com.example.cargotracker.externaldata.infrastructure.persistence;

import java.util.UUID;

/** external_data.data_source の 1 行のうち、情報源のリポジトリが読む列（Bolt 39b）。 */
public record DataSourceRow(UUID id, String provider, String recordType, String approvalStatus) {}
