import React, { useState, useEffect } from 'react';
import axios from 'axios';
import BrutalismButton from '../BrutalismButton';
import { FormattedMessage } from 'react-intl';
import getCollaborationNotifications from '../../models/notifications/getCollaborationNotifications';

export default function CollaborationMessages({
    collaborationTitle,
    collaborationData,
    setModalOpen, }) {

    // console.log('CollaborationMessages collaborationTitleData:', collaborationTitleData);

    const [loading, setLoading] = useState(false);
    const [showError, setShowError] = useState(false);
    const [error, setError] = useState('');

    const content = collaborationData?.map(data => {
        const username = data.getAttribute('data-username');
        const access = data.textContent.includes('view-only') ? 'View-Only' : 'Edit';
        const email = data.textContent.match(/\S+@\S+\.\S+/);
        const buttons = [...data.querySelectorAll('input[type="button"]')].map(button => {
            console.log('button class', button.getAttribute('class'));
            console.log('button value', button.getAttribute('value'));
            return {
                'class': button.getAttribute('class'),
                'value': button.getAttribute('value')
            }
        });
        const id = data.getAttribute('id');
        const collabString = id ? `&collabId=${id.replace("edit-", "")} :` : '';

        console.log(username, " ------", access, '========', id, '--------', collabString);

        return (
            <div style={{
                display: 'flex',
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
            }}>

                <h6>{username}{' '}({access}) {email}</h6>
                <div style={{
                    display: 'flex',
                    flexDirection: 'row',
                }}>
                    {buttons.map(button => (
                        <BrutalismButton
                            style={{
                                margin: '0 5px 10px 0',
                            }}
                            onClick={async () => {
                                setLoading(true);
                                console.log(button.class);
                                const response = await fetch(`/Collaborate?json=1&username=${username}&approve=${button.class}&actionForExisting=${button.class}${collabString}`);
                                const data = await response.json();
                                setLoading(false);
                                console.log(data);
                                if (data.error) {
                                    setShowError(true);
                                    setError(data.error);
                                } else {
                                    setShowError(false);
                                    getCollaborationNotifications();
                                    setModalOpen(false);
                                }

                            }}
                        >
                            {button.value}
                        </BrutalismButton>
                    ))}
                </div>
            </div>
        );
    })

    return <>
        {collaborationTitle ? <h5>{collaborationTitle}</h5> : <h5><FormattedMessage id="NO_NEW_MESSAGE" /></h5>}
        {content}
        {showError && <h6>{error}</h6>}
    </>
}


