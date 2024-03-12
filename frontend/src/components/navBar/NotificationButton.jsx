import React from 'react';
import { Button, Badge } from 'react-bootstrap';
import { Bell } from 'react-bootstrap-icons'; 

const NotificationButton = ({ count }) => {

  return (
    <div style={{
      minWidth: '35px',
    }}>
    <Button style={{
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
    }}>
      <Bell color="white" /> 
      {count > 0 && (
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
          {count}
        </Badge>
      )}
    </Button>
    </div>
  );
};

export default NotificationButton;
