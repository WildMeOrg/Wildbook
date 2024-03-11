import React from 'react';
import Dropdown from 'react-bootstrap/Dropdown';
import DownIcon from '../svg/DownIcon';


export default function MultiLanguageDropdown() {
    return (
        <div style={{
            backgroundColor: 'rgba(255, 255, 255, 0.2)',
            border: 'none',
            borderRadius: '30px',
            minWidth: '55px', 
            height: '35px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative', 
            // padding: '10px',
            margin: '10px',
        }}>
            <img src="/react/flags/uk.png" alt="uk" style={{width: '20px', height: '15px'}} />
                    <DownIcon />
            <Dropdown style={{
                    // backgroundColor: 'transparent',
                    border: 'none',
                    color: 'white',
                    display: 'flex',
                    flexDirection: 'row',
                    alignItems: 'center',
                    justifyContent: 'center',
                
                }}>
                <Dropdown.Toggle variant="basic" id="dropdown-basic" >
                    {/* <img src="/react/flags/uk.png" alt="uk" style={{width: '20px', height: '15px'}} />
                    <DownIcon /> */}
                </Dropdown.Toggle>
                
                <Dropdown.Menu>
                <Dropdown.Item href="#/action-1"><img src="/react/flags/uk.png" alt="uk" style={{width: '30px', height: '30px'}} /></Dropdown.Item>
                <Dropdown.Item href="#/action-2"><img src="/react/flags/uk.png" alt="uk" style={{width: '30px', height: '30px'}} /></Dropdown.Item>
                <Dropdown.Item href="#/action-3"><img src="/react/flags/uk.png" alt="uk" style={{width: '30px', height: '30px'}} /></Dropdown.Item>
                </Dropdown.Menu>
          </Dropdown>
          </div>
    )
}

