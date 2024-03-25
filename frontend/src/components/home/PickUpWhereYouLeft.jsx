import React from 'react';
import 'bootstrap/dist/css/bootstrap.min.css';
import LatestActivityItem from './LatestActivityItem';
import { formatDate } from '../../utils/formatters';
import { FormattedMessage } from 'react-intl';

const PickUp = ({data}) => {
    console.log('PickUp data', data);
    return (
        <div style={{
            marginTop: '40px',
            position: 'relative',
            height: '500px',
        }}>

            <div className="col-8" style={{
                padding: '10px',
                position: 'absolute',
                left: '100px',
                width: '500px',
                zIndex: 1,
            }}>
                <h1 style={{ fontSize: '4em' }}>
                    <FormattedMessage id='PICK_UP_PART1' />
                </h1>
                <h1 style={{ fontSize: '4em', }}>
                    <FormattedMessage id='PICK_UP_PART2' />
                </h1>
                <LatestActivityItem
                    name="LATEST_BULK_REPORT"
                    num={data?.latestBulkImportTask?.numberMediaAssets || '0'}
                    date={formatDate(data?.latestBulkImportTask?.dateTimeCreated, true)}
                    text={data?.latestBulkImportTask}
                    disabled={!data?.latestBulkImportTask}
                />
                <LatestActivityItem
                    name="LATEST_INDIVIDUAL"
                    date={formatDate(data?.latestBulkImportIndividual?.dateTimeCreated, true)}
                    text={data?.latestBulkImportIndividual}
                    disabled={!data?.latestBulkImportIndividual}
                />
                <LatestActivityItem
                    name="LATEST_MATCHING_ACTION"
                    date={formatDate(data?.latestMatchTask?.dateTimeCreated, true)}
                    text={data?.latestMatchTask}
                    disabled={!data?.latestMatchTask}
                />

            </div>
            <div style={{
                backgroundColor: '#cfe2ff',
                position: 'absolute',
                top: 0,
                left: '40%',
                bottom: '10%',
                borderRadius: '10px 0 0 10px',
                width: '60%',
            }}>
            </div>
            <div className="col-4"
                style={{
                    position: 'absolute',
                    top: '10%',
                    left: '55%',
                    width: '300px',
                    borderRadius: '10px',
                    height: '450px',
                    zIndex: 1,
                    backgroundImage: 'url(/react/images/pick.png)',
                }}
            >

            </div>

        </div>
    );
};

export default PickUp;
