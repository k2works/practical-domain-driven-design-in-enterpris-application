package com.example.cargotracker.externaldata.domain.model.aggregates;

/** メモリの実装で外部原本の保存の契約を確かめる（Bolt 39）。 */
class InMemoryExternalPayloadStorageTest extends ExternalPayloadStorageContract {

    private final InMemoryExternalPayloadStorage storage = new InMemoryExternalPayloadStorage();

    @Override
    protected ExternalPayloadStorage storage() {
        return storage;
    }
}
