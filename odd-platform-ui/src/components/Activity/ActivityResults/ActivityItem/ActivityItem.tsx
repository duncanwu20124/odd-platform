import React from 'react';
import { Grid, Typography } from '@mui/material';
import { EntityClassItem, LabelItem, TagItem } from 'components/shared/elements';
import { GearIcon, UserIcon } from 'components/shared/icons';
import { Link } from 'react-router-dom';
import { ActivityEventType } from 'generated-sources';
import {
  ActivityFieldHeader,
  AlertActivityField,
  ArrayActivityField,
  CustomGroupActivityField,
  EnumsActivityField,
  OwnerActivityField,
  StringActivityField,
  TermActivityField,
} from 'components/shared/elements/Activity';
import { useAppDateTime, useAppPaths } from 'lib/hooks';
import type { Activity } from 'redux/interfaces';
import DatasetTermActivityField from 'components/shared/elements/Activity/ActivityFields/DatasetTermActivityField/DatasetTermActivityField';
import * as S from './ActivityItemStyles';

interface ActivityItemProps {
  activity: Activity;
  hideAllDetails: boolean;
  dataQA?: string;
}

const ActivityItem: React.FC<ActivityItemProps> = ({
  activity,
  hideAllDetails,
  dataQA,
}) => {
  const { dataEntityOverviewPath } = useAppPaths();
  const { activityFormattedDateTime } = useAppDateTime();

  const tagStateItem = React.useCallback(
    (name: string, important: boolean | undefined) => (
      <TagItem sx={{ backgroundColor: 'white' }} label={name} important={important} />
    ),
    []
  );

  const labelStateItem = React.useCallback(
    (name: string) => <LabelItem labelName={name} />,
    []
  );

  const isTypeRelatedTo = (types: ActivityEventType[]) =>
    types.includes(activity.eventType);

  return (
    <S.Container container data-qa={dataQA}>
      <Grid container justifyContent='space-between' flexWrap='nowrap'>
        <Grid item display='flex' flexWrap='nowrap' alignItems='center'>
          <Link to={dataEntityOverviewPath(activity.dataEntity.id)}>
            <Typography variant='h3' sx={{ mr: 1, width: 'max-content' }}>
              {activity.dataEntity.externalName || activity.dataEntity.internalName}
            </Typography>
          </Link>
          {activity.dataEntity.entityClasses?.map(entityClass => (
            <EntityClassItem
              key={entityClass.id}
              sx={{ mr: 0.5 }}
              entityClassName={entityClass.name}
            />
          ))}
        </Grid>
        <Grid
          item
          container
          flexWrap='nowrap'
          justifyContent='flex-end'
          alignItems='center'
        >
          {activity.systemEvent ? (
            <GearIcon />
          ) : (
            <Grid display='flex' flexWrap='nowrap' alignItems='center'>
              <UserIcon stroke='black' />
              <Typography variant='body1' sx={{ ml: 0.5 }}>
                {activity.createdBy?.owner?.name || activity.createdBy?.identity.username}
              </Typography>
            </Grid>
          )}
          <Typography variant='subtitle1' sx={{ ml: 0.5 }}>
            at {activityFormattedDateTime(activity.createdAt)}
          </Typography>
        </Grid>
      </Grid>
      {isTypeRelatedTo([
        ActivityEventType.OWNERSHIP_CREATED,
        ActivityEventType.OWNERSHIP_UPDATED,
        ActivityEventType.OWNERSHIP_DELETED,
      ]) && (
        <OwnerActivityField
          oldState={activity.oldState.ownerships}
          newState={activity.newState.ownerships}
          eventType={activity.eventType}
          hideAllDetails={hideAllDetails}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.DATA_ENTITY_CREATED]) && (
        <StringActivityField
          startText='Data entity with'
          activityName='ODDRN'
          oldState={activity.oldState.dataEntity?.oddrn}
          newState={activity.newState.dataEntity?.oddrn}
          hideAllDetails={hideAllDetails}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.DESCRIPTION_UPDATED]) && (
        <StringActivityField
          activityName='Description'
          oldState={activity.oldState.description?.description}
          newState={activity.newState.description?.description}
          hideAllDetails={hideAllDetails}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.BUSINESS_NAME_UPDATED]) && (
        <StringActivityField
          activityName='Business name'
          oldState={activity.oldState.businessName?.internalName}
          newState={activity.newState.businessName?.internalName}
          hideAllDetails={hideAllDetails}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.DATASET_FIELD_DESCRIPTION_UPDATED]) && (
        <StringActivityField
          activityName={`Dataset field ${activity.oldState.datasetFieldInformation?.name} description`}
          oldState={activity.oldState.datasetFieldInformation?.description}
          newState={activity.newState.datasetFieldInformation?.description}
          hideAllDetails={hideAllDetails}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.TAG_ASSIGNMENT_UPDATED]) && (
        <ArrayActivityField
          activityName='Tags'
          oldState={activity.oldState.tags}
          newState={activity.newState.tags}
          hideAllDetails={hideAllDetails}
          stateItem={tagStateItem}
          plural
        />
      )}
      {isTypeRelatedTo([ActivityEventType.DATASET_FIELD_LABELS_UPDATED]) && (
        <ArrayActivityField
          activityName={`Labels in ${activity.oldState.datasetFieldInformation?.name} column`}
          oldState={activity.oldState.datasetFieldInformation?.labels}
          newState={activity.newState.datasetFieldInformation?.labels}
          hideAllDetails={hideAllDetails}
          stateItem={labelStateItem}
          plural
        />
      )}
      {isTypeRelatedTo([ActivityEventType.TERM_ASSIGNMENT_UPDATED]) && (
        <TermActivityField
          oldState={activity.oldState.terms}
          newState={activity.newState.terms}
          hideAllDetails={hideAllDetails}
          eventType='updated'
          stateDirection='column'
        />
      )}
      {isTypeRelatedTo([ActivityEventType.DATASET_FIELD_TERM_ASSIGNMENT_UPDATED]) && (
        <DatasetTermActivityField
          oldState={activity.oldState.datasetFieldTerms}
          newState={activity.newState.datasetFieldTerms}
          hideAllDetails={hideAllDetails}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.DATASET_FIELD_VALUES_UPDATED]) && (
        <EnumsActivityField
          oldState={activity.oldState.datasetFieldValues}
          newState={activity.newState.datasetFieldValues}
          hideAllDetails={hideAllDetails}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.CUSTOM_GROUP_CREATED]) && (
        <ActivityFieldHeader
          eventType='created'
          startText='Custom group'
          activityName={`${activity.dataEntity.internalName}`}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.CUSTOM_GROUP_DELETED]) && (
        <ActivityFieldHeader
          eventType='deleted'
          startText='Custom group'
          activityName={`${activity.dataEntity.internalName}`}
        />
      )}
      {isTypeRelatedTo([ActivityEventType.CUSTOM_GROUP_UPDATED]) && (
        <CustomGroupActivityField
          oldState={activity.oldState.customGroup}
          newState={activity.newState.customGroup}
          hideAllDetails={hideAllDetails}
        />
      )}
      {isTypeRelatedTo([
        ActivityEventType.ALERT_HALT_CONFIG_UPDATED,
        ActivityEventType.ALERT_STATUS_UPDATED,
        ActivityEventType.OPEN_ALERT_RECEIVED,
        ActivityEventType.RESOLVED_ALERT_RECEIVED,
      ]) && (
        <AlertActivityField
          eventType={activity.eventType}
          oldState={activity.oldState}
          newState={activity.newState}
        />
      )}
    </S.Container>
  );
};

export default ActivityItem;
