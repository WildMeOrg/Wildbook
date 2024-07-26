import React, { useState } from 'react';
import { Button, Offcanvas } from 'react-bootstrap';
import 'bootstrap/dist/css/bootstrap.min.css';
import { FormattedMessage } from 'react-intl';
import Chip from '../Chip';
import BrutalismButton from '../BrutalismButton';
import ThemeContext from '../../ThemeColorProvider'; 

function Sidebar({
  formFilters,
  setFilterPanel,
  setFormFilters,
}) {
  const theme = React.useContext(ThemeContext);
  const [show, setShow] = useState(false);
  const sidebarWidth = 400;

  const handleClose = () => setShow(false);
  const handleShow = () => setShow(true);

  const num = formFilters.length;
  
  return (
    <>
      <Button
        onClick={handleShow}
        style={{
          borderRadius: '0 0 5px 5px',
          width: '200px',
          height: '45px',
          backgroundColor: 'rgb(0, 117, 153)',
          border: "none",
          color: 'white',
          position: 'fixed',
          top: '50%',
          right: show ? `${sidebarWidth}px` : '0px',
          zIndex: '1030',
          transform: 'translateY(-50%) translateX(42%) rotate(90deg)',
          transition: 'right 0.3s ease-in-out',
          padding: '8px 10px',
          display: 'flex',
          flexDirection: 'row',
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <FormattedMessage id="APPLIED_FILTERS" />
        <div style={{
          width: "20px",
          height: "20px",
          borderRadius: "50%",
          backgroundColor: 'red',
          color: 'white',
          marginLeft: "10px",
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
        }}>{num}</div>
      </Button>

      <Offcanvas show={show} onHide={handleClose} placement="end" style={{ width: `${sidebarWidth}px`, borderRadius: '10px 0 0 10px' }}>
        <Offcanvas.Header closeButton>
          <Offcanvas.Title>Applied Filters</Offcanvas.Title>
        </Offcanvas.Header>
        <Offcanvas.Body style={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between'
        }}>
          <div style={{ overflowY: 'auto' }}>
            {formFilters.map((filter, index) => (
              <Chip key={index}>
                {filter}
              </Chip>
            ))}
          </div>
          <div className='d-flex justify-content-between align-items-center'
            style={{
              padding: '10px 0',
            }}>
            <BrutalismButton
              onClick={() => {
                setFilterPanel(true);
                handleClose();
              }}
              noArrow={true}
              backgroundColor= {theme.primaryColors.primary700}
              borderColor={theme.primaryColors.primary700}
              color='white'
            >
              <FormattedMessage id="FILTER_COPY" defaultMessage={"Copy"}/>
            </BrutalismButton>
            <BrutalismButton
              onClick={() => {
                setFilterPanel(true);
                handleClose();
              }}
              backgroundColor= {theme.primaryColors.primary700}
              borderColor={theme.primaryColors.primary700}
              color='white'
              noArrow={true}
            >
              <FormattedMessage id="FILTER_EDIT_FILTER" defaultMessage={"Edit"}/>
            </BrutalismButton>
            <BrutalismButton
              borderColor={theme.primaryColors.primary700}
              color={theme.primaryColors.primary700}
              noArrow
              onClick={() => {
                setFormFilters([]);
                handleClose();
                // setFilterPanel(false);
                // localStorage.removeItem("formData");
                window.location.reload();
              }}
            >
              <FormattedMessage id="FILTER_RESET_FILTER" defaultMessage={"Reset"}/>
            </BrutalismButton>
          </div>
        </Offcanvas.Body>

      </Offcanvas>
    </>
  );
}

export default Sidebar;
