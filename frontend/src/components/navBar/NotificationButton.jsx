import React, { useState } from 'react';
import { Button, Badge } from 'react-bootstrap';
import { Bell } from 'react-bootstrap-icons';
import Modal from 'react-bootstrap/Modal';
import BrutalismButton from '../BrutalismButton';

const NotificationButton = ({ notificationTitle, notificationData, getNotifications }) => {

  const [modalOpen, setModalOpen] = React.useState(false);

  const handleBlur = (e) => {
    console.log(111, e);
    // if (!e.currentTarget.contains(e.relatedTarget)) {
    //   setModalOpen(true); 
    // }
  };
  console.log('NotificationButton notificationData:', notificationData);
  const content = notificationData?.map(data => {
    const username = data.getAttribute('data-username');
    const access = data.textContent.includes('view-only') ? 'View-Only' : 'Edit';
    const email = data.textContent.match(/\S+@\S+\.\S+/);
    const buttons = [...data.querySelectorAll('input[type="button"]')].map(button => {
      console.log('button class',button.getAttribute('class'));
      console.log('button value',button.getAttribute('value'));
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
                console.log(button.class);
                const response = await fetch(`/Collaborate?json=1&username=${username}&approve=${button.class}&actionForExisting=${button.class}${collabString}`);
                const data = await response.json();
                console.log(data);
                getNotifications();
                setModalOpen(false);                
              }}
            >
              {button.value}
            </BrutalismButton>
          ))}
        </div>
      </div>
    );
  })
  return (
    <div
      style={{
        minWidth: '35px',
        position: 'relative',
      }}
      tabIndex={0}
      onBlur={handleBlur}
    >
      <Modal.Dialog style={{
        position: 'absolute',
        top: '50px',
        right: '-150px',
        zIndex: '100',
        width: '800px',
        display: modalOpen ? 'block' : 'none',
        backgroundColor: '#E5F6FF',
        boxShadow: "4px 4px 0px #CCF0FF",
        padding: '20px',
        borderRadius: '10px',
      }}>

        <Modal.Body style={{
          display: 'flex',
          flexDirection: 'column',
        }}>
          {notificationTitle? <h5>{notificationTitle}</h5> : <h5>No new message</h5>}
          {content}
        </Modal.Body>

      </Modal.Dialog>
      <Button
        style={{
          backgroundColor: 'rgba(255, 255, 255, 0.25)',
          border: 'none',
          borderRadius: '50%',
          minWidth: '35px',
          height: '35px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          padding: 0,
        }}
        onClick={() => {
          setModalOpen(!modalOpen);
        }}
      >
        <Bell color="white" />
        {notificationTitle && (
          <Badge pill bg="danger" style={{
            width: '12px',
            height: '12px',
            position: 'absolute',
            top: '22px',
            left: '22px',
            padding: '0 4px',
            fontSize: '0.5rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
            {notificationData?.length}
          </Badge>
        )}
      </Button>
    </div>
  );
};

export default NotificationButton;
