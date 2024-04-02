import React, { useState } from 'react';
import { Button, Badge } from 'react-bootstrap';
import { Bell } from 'react-bootstrap-icons';
import Modal from 'react-bootstrap/Modal';
import CollaborationMessages from './CollaborationMessages';
import MergeMessages from './MergeMessages';

const NotificationButton = ({ 
  collaborationTitle, 
  collaborationData, 
  mergeData,
}) => {

  const [modalOpen, setModalOpen] = React.useState(false);

  // const { notifications: mergeData, error: mergeError, loading: mergeLoading } = useGetCollaborationNotifications();

//   const mergeData = [
//     {
//       "primaryIndividualName": "merge002",
//       "mergeExecutionDate": "2024/04/15",
//       "secondaryIndividualName": "merge001",
//       "secondaryIndividualId": "a05ac02d-dc6f-47c8-a51a-42767b3837a5",
//       "initiator": "erin1",
//       "ownedByMe": "false",
//       "notificationType": "mergePending",
//       "taskId": "31a34e01-4b99-499d-bcb4-9084731eef25",
//       "primaryIndividualId": "53d2eaf3-065e-46ee-88d4-e614e1261e43"
//   },
//   {
//     "primaryIndividualName": "merge002",
//     "mergeExecutionDate": "2024/04/15",
//     "secondaryIndividualName": "merge001",
//     "secondaryIndividualId": "a05ac02d-dc6f-47c8-a51a-42767b3837a5",
//     "initiator": "erin1",
//     "ownedByMe": "false",
//     "notificationType": "mergeComplete",
//     "taskId": "31a34e01-4b99-499d-bcb4-9084731eef25",
//     "primaryIndividualId": "53d2eaf3-065e-46ee-88d4-e614e1261e43"
// },
// {
//   "primaryIndividualName": "merge002",
//   "mergeExecutionDate": "2024/04/15",
//   "secondaryIndividualName": "merge001",
//   "secondaryIndividualId": "a05ac02d-dc6f-47c8-a51a-42767b3837a5",
//   "initiator": "erin2",
//   "deniedBy": "erin999",
//   "ownedByMe": "false",
//   "notificationType": "mergeDenied",
//   "taskId": "31a34e01-4b99-499d-bcb4-9084731eef25",
//   "primaryIndividualId": "53d2eaf3-065e-46ee-88d4-e614e1261e43"
// },]
//   console.log('NotificationButton mergeData:', mergeData);

  const handleBlur = (e) => {
    console.log(111, e);
    // if (!e.currentTarget.contains(e.relatedTarget)) {
    //   setModalOpen(true); 
    // }
  };
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
        right: '-50px',
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
          maxHeight: '1000px',
          contentOverflow: 'scroll',
        }}>
          <CollaborationMessages
            collaborationTitle={collaborationTitle}
            collaborationData={collaborationData}
            mergeData={mergeData}
            setModalOpen={setModalOpen}
          />
          <MergeMessages 
            mergeData={mergeData}
            setModalOpen={setModalOpen}
          />
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
        {collaborationTitle && (
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
            {collaborationData?.length}
          </Badge>
        )}
      </Button>
    </div>
  );
};

export default NotificationButton;
