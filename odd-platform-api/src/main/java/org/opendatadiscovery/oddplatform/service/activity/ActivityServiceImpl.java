package org.opendatadiscovery.oddplatform.service.activity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opendatadiscovery.oddplatform.api.contract.model.Activity;
import org.opendatadiscovery.oddplatform.api.contract.model.ActivityCountInfo;
import org.opendatadiscovery.oddplatform.api.contract.model.ActivityEventType;
import org.opendatadiscovery.oddplatform.api.contract.model.ActivityType;
import org.opendatadiscovery.oddplatform.auth.AuthIdentityProvider;
import org.opendatadiscovery.oddplatform.dto.activity.ActivityContextInfo;
import org.opendatadiscovery.oddplatform.dto.activity.ActivityCreateEvent;
import org.opendatadiscovery.oddplatform.dto.activity.ActivityEventTypeDto;
import org.opendatadiscovery.oddplatform.dto.lineage.LineageStreamKind;
import org.opendatadiscovery.oddplatform.dto.security.UserDto;
import org.opendatadiscovery.oddplatform.exception.BadUserRequestException;
import org.opendatadiscovery.oddplatform.mapper.ActivityMapper;
import org.opendatadiscovery.oddplatform.model.tables.pojos.ActivityPojo;
import org.opendatadiscovery.oddplatform.repository.reactive.ReactiveActivityRepository;
import org.opendatadiscovery.oddplatform.service.DataEntityService;
import org.opendatadiscovery.oddplatform.service.activity.handler.ActivityHandler;
import org.opendatadiscovery.oddplatform.service.ingestion.util.DateTimeUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static reactor.function.TupleUtils.function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityServiceImpl implements ActivityService {
    private final ReactiveActivityRepository activityRepository;
    private final DataEntityService dataEntityService;
    private final AuthIdentityProvider authIdentityProvider;
    private final ActivityMapper activityMapper;
    private final List<ActivityHandler> handlers;

    @Override
    public Mono<Void> createActivityEvent(final ActivityCreateEvent event) {
        final LocalDateTime activityCreateTime = DateTimeUtil.generateNow();
        return authIdentityProvider.getCurrentUser()
            .map(UserDto::username)
            .map(username -> activityMapper.mapToPojo(event, activityCreateTime, username))
            .switchIfEmpty(Mono.defer(() -> Mono.just(activityMapper.mapToPojo(event, activityCreateTime, null))))
            .flatMap(activityRepository::saveReturning)
            .then();
    }

    @Override
    public Mono<Void> createActivityEvents(final List<ActivityCreateEvent> events) {
        final LocalDateTime activityCreateTime = DateTimeUtil.generateNow();
        return authIdentityProvider.getCurrentUser()
            .map(UserDto::username)
            .flatMapMany(username -> Flux.fromStream(mapEventsToPojos(events, activityCreateTime, username)))
            .switchIfEmpty(Flux.fromStream(mapEventsToPojos(events, activityCreateTime, null)))
            .collectList()
            .flatMap(activityRepository::save);
    }

    @Override
    public Mono<ActivityContextInfo> getContextInfo(final Map<String, Object> parameters,
                                                    final ActivityEventTypeDto eventType) {
        return getActivityHandler(eventType).getContextInfo(parameters);
    }

    @Override
    public Mono<Map<Long, String>> getUpdatedInfo(final Map<String, Object> parameters,
                                                  final List<Long> dataEntityIds,
                                                  final ActivityEventTypeDto eventType) {
        return getActivityHandler(eventType).getUpdatedState(parameters, dataEntityIds);
    }

    @Override
    public Mono<String> getUpdatedInfo(final Map<String, Object> parameters,
                                       final Long dataEntityId,
                                       final ActivityEventTypeDto eventType) {
        return getActivityHandler(eventType).getUpdatedState(parameters, dataEntityId);
    }

    @Override
    public Flux<Activity> getActivityList(final OffsetDateTime beginDate,
                                          final OffsetDateTime endDate,
                                          final Integer size,
                                          final Long datasourceId,
                                          final Long namespaceId,
                                          final List<Long> tagIds,
                                          final List<Long> ownerIds,
                                          final List<Long> userIds,
                                          final ActivityType type,
                                          final ActivityEventType eventType,
                                          final Long lastEventId,
                                          final OffsetDateTime lastEventDateTime) {
        if (beginDate == null || endDate == null) {
            return Flux.error(new BadUserRequestException("Begin date and end date can't be null"));
        }
        final ActivityEventTypeDto eventTypeDto =
            eventType != null ? ActivityEventTypeDto.valueOf(eventType.name()) : null;
        if (type == null) {
            return fetchAllActivities(beginDate, endDate, size, datasourceId, namespaceId, tagIds, ownerIds,
                userIds, eventTypeDto, lastEventId, lastEventDateTime);
        }
        return switch (type) {
            case MY_OBJECTS -> fetchMyActivities(beginDate, endDate, size, datasourceId, namespaceId, tagIds, userIds,
                eventTypeDto, lastEventId, lastEventDateTime);
            case DOWNSTREAM -> fetchDependentActivities(beginDate, endDate, size, datasourceId, namespaceId, tagIds,
                userIds, eventTypeDto, lastEventId, lastEventDateTime, LineageStreamKind.DOWNSTREAM);
            case UPSTREAM -> fetchDependentActivities(beginDate, endDate, size, datasourceId, namespaceId, tagIds,
                userIds, eventTypeDto, lastEventId, lastEventDateTime, LineageStreamKind.UPSTREAM);
            case ALL -> fetchAllActivities(beginDate, endDate, size, datasourceId, namespaceId, tagIds, ownerIds,
                userIds, eventTypeDto, lastEventId, lastEventDateTime);
        };
    }

    @Override
    public Flux<Activity> getDataEntityActivityList(final OffsetDateTime beginDate,
                                                    final OffsetDateTime endDate,
                                                    final Integer size,
                                                    final Long dataEntityId,
                                                    final List<Long> userIds,
                                                    final ActivityEventType eventType,
                                                    final Long lastEventId,
                                                    final OffsetDateTime lastEventDateTime) {
        if (beginDate == null || endDate == null) {
            return Flux.error(new BadUserRequestException("Begin date and end date can't be null"));
        }
        final ActivityEventTypeDto eventTypeDto =
            eventType != null ? ActivityEventTypeDto.valueOf(eventType.name()) : null;
        return activityRepository.findDataEntityActivities(beginDate, endDate, size, dataEntityId, userIds,
                eventTypeDto, lastEventId, lastEventDateTime)
            .map(activityMapper::mapToActivity);
    }

    @Override
    public Mono<ActivityCountInfo> getActivityCounts(final OffsetDateTime beginDate,
                                                     final OffsetDateTime endDate,
                                                     final Long datasourceId,
                                                     final Long namespaceId,
                                                     final List<Long> tagIds,
                                                     final List<Long> ownerIds,
                                                     final List<Long> userIds,
                                                     final ActivityEventType eventType) {
        final ActivityEventTypeDto eventTypeDto =
            eventType != null ? ActivityEventTypeDto.valueOf(eventType.name()) : null;
        final Mono<Long> totalCount =
            getTotalCount(beginDate, endDate, datasourceId, namespaceId, tagIds, ownerIds, userIds, eventTypeDto);
        final Mono<Long> myObjectActivitiesCount =
            getMyObjectActivitiesCount(beginDate, endDate, datasourceId, namespaceId,
                tagIds, userIds, eventTypeDto);
        final Mono<Long> downstreamActivitiesCount = getDependentActivitiesCount(beginDate, endDate, datasourceId,
            namespaceId, tagIds, userIds, eventTypeDto, LineageStreamKind.DOWNSTREAM);
        final Mono<Long> upstreamActivitiesCount = getDependentActivitiesCount(beginDate, endDate, datasourceId,
            namespaceId, tagIds, userIds, eventTypeDto, LineageStreamKind.UPSTREAM);
        return Mono.zip(totalCount, myObjectActivitiesCount, downstreamActivitiesCount, upstreamActivitiesCount)
            .map(function(
                (total, myObjectsCount, downstreamCount, upstreamCount) -> new ActivityCountInfo()
                    .totalCount(total)
                    .myObjectsCount(myObjectsCount)
                    .downstreamCount(downstreamCount)
                    .upstreamCount(upstreamCount)
            ));
    }

    private Flux<Activity> fetchAllActivities(final OffsetDateTime beginDate,
                                              final OffsetDateTime endDate,
                                              final Integer size,
                                              final Long datasourceId,
                                              final Long namespaceId,
                                              final List<Long> tagIds,
                                              final List<Long> ownerIds,
                                              final List<Long> userIds,
                                              final ActivityEventTypeDto eventType,
                                              final Long lastEventId,
                                              final OffsetDateTime lastEventDateTime) {
        return activityRepository.findAllActivities(beginDate, endDate, size, datasourceId, namespaceId, tagIds,
                ownerIds, userIds, eventType, lastEventId, lastEventDateTime)
            .map(activityMapper::mapToActivity);
    }

    private Flux<Activity> fetchMyActivities(final OffsetDateTime beginDate,
                                             final OffsetDateTime endDate,
                                             final Integer size,
                                             final Long datasourceId,
                                             final Long namespaceId,
                                             final List<Long> tagIds,
                                             final List<Long> userIds,
                                             final ActivityEventTypeDto eventType,
                                             final Long lastEventId,
                                             final OffsetDateTime lastEventDateTime) {
        return authIdentityProvider.fetchAssociatedOwner()
            .flatMapMany(owner -> activityRepository.findMyActivities(beginDate, endDate, size, datasourceId,
                namespaceId, tagIds, userIds, eventType, owner.getId(), lastEventId, lastEventDateTime))
            .map(activityMapper::mapToActivity)
            .switchIfEmpty(Flux.empty());
    }

    private Flux<Activity> fetchDependentActivities(final OffsetDateTime beginDate,
                                                    final OffsetDateTime endDate,
                                                    final Integer size,
                                                    final Long datasourceId,
                                                    final Long namespaceId,
                                                    final List<Long> tagIds,
                                                    final List<Long> userIds,
                                                    final ActivityEventTypeDto eventType,
                                                    final Long lastEventId,
                                                    final OffsetDateTime lastEventDateTime,
                                                    final LineageStreamKind lineageStreamKind) {
        return dataEntityService.getDependentDataEntityOddrns(lineageStreamKind)
            .flatMapMany(oddrns -> activityRepository.findDependentActivities(beginDate, endDate, size, datasourceId,
                namespaceId, tagIds, userIds, eventType, oddrns, lastEventId, lastEventDateTime))
            .map(activityMapper::mapToActivity)
            .switchIfEmpty(Flux.empty());
    }

    private Mono<Long> getTotalCount(final OffsetDateTime beginDate,
                                     final OffsetDateTime endDate,
                                     final Long datasourceId,
                                     final Long namespaceId,
                                     final List<Long> tagIds,
                                     final List<Long> ownerIds,
                                     final List<Long> userIds,
                                     final ActivityEventTypeDto eventType) {
        return activityRepository.getTotalActivitiesCount(beginDate, endDate, datasourceId, namespaceId, tagIds,
                ownerIds, userIds, eventType)
            .defaultIfEmpty(0L);
    }

    private Mono<Long> getMyObjectActivitiesCount(final OffsetDateTime beginDate,
                                                  final OffsetDateTime endDate,
                                                  final Long datasourceId,
                                                  final Long namespaceId,
                                                  final List<Long> tagIds,
                                                  final List<Long> userIds,
                                                  final ActivityEventTypeDto eventType) {
        return authIdentityProvider.fetchAssociatedOwner()
            .flatMap(
                owner -> activityRepository.getMyObjectsActivitiesCount(beginDate, endDate, datasourceId, namespaceId,
                    tagIds, userIds, eventType, owner.getId()))
            .defaultIfEmpty(0L);
    }

    private Mono<Long> getDependentActivitiesCount(final OffsetDateTime beginDate,
                                                   final OffsetDateTime endDate,
                                                   final Long datasourceId,
                                                   final Long namespaceId,
                                                   final List<Long> tagIds,
                                                   final List<Long> userIds,
                                                   final ActivityEventTypeDto eventType,
                                                   final LineageStreamKind lineageStreamKind) {
        return dataEntityService.getDependentDataEntityOddrns(lineageStreamKind)
            .flatMap(oddrns -> activityRepository.getDependentActivitiesCount(beginDate, endDate, datasourceId,
                namespaceId, tagIds, userIds, eventType, oddrns))
            .defaultIfEmpty(0L);
    }

    private ActivityHandler getActivityHandler(final ActivityEventTypeDto eventType) {
        return handlers.stream().filter(handler -> handler.isHandle(eventType))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Can't find handler for event type " + eventType.name()));
    }

    private Stream<ActivityPojo> mapEventsToPojos(final List<ActivityCreateEvent> events,
                                                  final LocalDateTime createTime,
                                                  final String username) {
        return events
            .stream()
            .map(event -> activityMapper.mapToPojo(event, createTime, username));
    }
}
