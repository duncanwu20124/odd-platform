package org.opendatadiscovery.oddplatform.repository.reactive;

import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.opendatadiscovery.oddplatform.dto.DatasetFieldWithLabelsDto;
import org.opendatadiscovery.oddplatform.model.tables.pojos.DatasetFieldPojo;
import org.opendatadiscovery.oddplatform.model.tables.pojos.LabelPojo;
import org.opendatadiscovery.oddplatform.model.tables.records.DatasetFieldRecord;
import org.opendatadiscovery.oddplatform.repository.util.JooqQueryHelper;
import org.opendatadiscovery.oddplatform.repository.util.JooqReactiveOperations;
import org.opendatadiscovery.oddplatform.repository.util.JooqRecordHelper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.jsonArrayAgg;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.partitionBy;
import static org.opendatadiscovery.oddplatform.model.Tables.DATASET_FIELD;
import static org.opendatadiscovery.oddplatform.model.Tables.DATASET_STRUCTURE;
import static org.opendatadiscovery.oddplatform.model.Tables.DATASET_VERSION;
import static org.opendatadiscovery.oddplatform.model.Tables.DATA_ENTITY;
import static org.opendatadiscovery.oddplatform.model.Tables.LABEL;
import static org.opendatadiscovery.oddplatform.model.Tables.LABEL_TO_DATASET_FIELD;

@Repository
@Slf4j
public class ReactiveDatasetFieldRepositoryImpl
    extends ReactiveAbstractCRUDRepository<DatasetFieldRecord, DatasetFieldPojo>
    implements ReactiveDatasetFieldRepository {

    private final JooqRecordHelper jooqRecordHelper;

    public ReactiveDatasetFieldRepositoryImpl(final JooqReactiveOperations jooqReactiveOperations,
                                              final JooqQueryHelper jooqQueryHelper,
                                              final JooqRecordHelper jooqRecordHelper) {
        super(jooqReactiveOperations, jooqQueryHelper, DATASET_FIELD, DatasetFieldPojo.class);
        this.jooqRecordHelper = jooqRecordHelper;
    }

    @Override
    public Mono<DatasetFieldPojo> updateDescription(final long datasetFieldId,
                                                    final String description) {
        final String newDescription = StringUtils.isEmpty(description) ? null : description;
        final var updateQuery = DSL.update(DATASET_FIELD)
            .set(DATASET_FIELD.INTERNAL_DESCRIPTION, newDescription)
            .where(DATASET_FIELD.ID.eq(datasetFieldId)).returning();
        return jooqReactiveOperations.mono(updateQuery).map(this::recordToPojo);
    }

    @Override
    public Flux<DatasetFieldPojo> getLastVersionDatasetFieldsByOddrns(final List<String> oddrns) {
        return jooqReactiveOperations.executeInPartitionReturning(oddrns, partitionedOddrns -> {
            final String version = "version";
            final String maxVersion = "max_version";
            final Name cteName = name("cte");

            final var cte = cteName.as(DSL.select(DATASET_FIELD.fields())
                .select(DATASET_VERSION.VERSION.as(version))
                .select(DSL.max(DATASET_VERSION.VERSION).over(partitionBy(DATASET_FIELD.ODDRN)).as(maxVersion))
                .from(DATASET_FIELD)
                .join(DATASET_STRUCTURE).on(DATASET_STRUCTURE.DATASET_FIELD_ID.eq(DATASET_FIELD.ID))
                .join(DATASET_VERSION).on(DATASET_STRUCTURE.DATASET_VERSION_ID.eq(DATASET_VERSION.ID))
                .where(DATASET_FIELD.ODDRN.in(partitionedOddrns)));

            final var query = DSL.with(cte)
                .select(cte.fields())
                .from(cte.getName())
                .where(cte.field(version, Long.class).eq(cte.field(maxVersion, Long.class)));
            return jooqReactiveOperations.flux(query);
        }).map(r -> r.into(DatasetFieldPojo.class));
    }

    @Override
    public Mono<Long> getDataEntityIdByDatasetFieldId(final long datasetFieldId) {
        final var query = DSL.select(DATA_ENTITY.ID)
            .from(DATASET_FIELD)
            .join(DATASET_STRUCTURE).on(DATASET_STRUCTURE.DATASET_FIELD_ID.eq(DATASET_FIELD.ID))
            .join(DATASET_VERSION).on(DATASET_STRUCTURE.DATASET_VERSION_ID.eq(DATASET_VERSION.ID))
            .join(DATA_ENTITY).on(DATASET_VERSION.DATASET_ODDRN.eq(DATA_ENTITY.ODDRN))
            .where(DATASET_FIELD.ID.eq(datasetFieldId));
        return jooqReactiveOperations.mono(query)
            .map(Record1::value1);
    }

    @Override
    public Mono<DatasetFieldWithLabelsDto> getDatasetFieldWithLabels(final long datasetFieldId) {
        final var query = DSL.select(DATASET_FIELD.fields())
            .select(jsonArrayAgg(field(LABEL.asterisk().toString())).as("labels"))
            .from(DATASET_FIELD)
            .leftJoin(LABEL_TO_DATASET_FIELD).on(DATASET_FIELD.ID.eq(LABEL_TO_DATASET_FIELD.DATASET_FIELD_ID))
            .leftJoin(LABEL).on(LABEL_TO_DATASET_FIELD.LABEL_ID.eq(LABEL.ID)).and(LABEL.DELETED_AT.isNull())
            .where(DATASET_FIELD.ID.eq(datasetFieldId))
            .groupBy(DATASET_FIELD.fields());

        return jooqReactiveOperations.mono(query)
            .map(this::mapRecordToDatasetFieldWithLabels);
    }

    @NotNull
    private DatasetFieldWithLabelsDto mapRecordToDatasetFieldWithLabels(final Record datasetFieldRecord) {
        final DatasetFieldPojo pojo = datasetFieldRecord.into(DatasetFieldPojo.class);
        final Set<LabelPojo> labels = jooqRecordHelper
            .extractAggRelation(datasetFieldRecord, "labels", LabelPojo.class);

        return new DatasetFieldWithLabelsDto(pojo, labels);
    }
}
