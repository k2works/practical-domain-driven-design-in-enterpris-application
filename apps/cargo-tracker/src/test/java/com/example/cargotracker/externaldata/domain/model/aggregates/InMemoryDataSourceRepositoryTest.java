package com.example.cargotracker.externaldata.domain.model.aggregates;

/** メモリの実装で情報源のリポジトリの契約を確かめる（Bolt 39b）。 */
class InMemoryDataSourceRepositoryTest extends DataSourceRepositoryContract {

    private final InMemoryDataSourceRepository repository = new InMemoryDataSourceRepository();

    @Override
    protected DataSourceRepository repository() {
        return repository;
    }

    @Override
    protected void given(DataSource source) {
        repository.add(source);
    }
}
