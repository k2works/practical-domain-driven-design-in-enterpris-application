package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.UUID;

/** メモリの実装で採用値のリポジトリの契約を確かめる（Bolt 39b3）。 */
class InMemoryAdoptedValueRepositoryTest extends AdoptedValueRepositoryContract {

    private final InMemoryAdoptedValueRepository adoptedValues = new InMemoryAdoptedValueRepository();

    @Override
    protected AdoptedValueRepository adoptedValues() {
        return adoptedValues;
    }

    @Override
    protected UUID givenReceivedRecord() {
        // メモリの実装は受信記録への外部キーを写さない
        return UUID.randomUUID();
    }
}
