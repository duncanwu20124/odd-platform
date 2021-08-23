import { Theme, createStyles, WithStyles } from '@material-ui/core';

export const alertsMainColWidthStyles = {
  col: {
    display: 'flex',
    alignItems: 'center',
    overflow: 'hidden',
    paddingRight: '8px',
    paddingLeft: '8px',
    '&:last-of-type': {
      paddingRight: 0,
    },
  },
  colName: {
    flex: '0 0 12%',
  },
  colDescription: {
    flex: '0 0 44%',
  },
  colUpdatedBy: {
    flex: '0 0 9%',
  },
  colStatus: {
    flex: '0 0 7%',
  },
  colCreatedTime: {
    flex: '0 0 12%',
  },
  colUpdatedAt: {
    flex: '0 0 12%',
  },
  colActionBtn: {
    flex: '0 0 3%',
    overflow: 'visible',
  },
};

export const styles = (theme: Theme) =>
  createStyles({
    container: {},
    alertsTableHeader: {
      color: '#B3BAC5',
      borderBottom: '1px solid #EBECF0',
    },
    ...alertsMainColWidthStyles,
  });

export type StylesType = WithStyles<typeof styles>;