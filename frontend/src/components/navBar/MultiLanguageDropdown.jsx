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
                    <img src="/react/flags/uk.png" alt="uk" style={{width: '20px', height: '12px'}} />
                    <DownIcon />
                </Dropdown.Toggle>
                
                <Dropdown.Menu
                    style={{
                        height: '100px',
                        width: '20px'
                    }}
                >
                    <Dropdown.Item href="#/action-1">
                        <img 
                            src="/react/flags/uk.png" alt="uk" 
                            style={{width: '20px', height: '12px', marginRight: '10px'}} />
                        English
                    </Dropdown.Item>
                    <Dropdown.Item href="#/action-1">
                        <img 
                            src="/react/flags/uk.png" alt="uk" 
                            style={{width: '20px', height: '12px', marginRight: '10px'}} />
                        French
                    </Dropdown.Item>
                    <Dropdown.Item href="#/action-1">
                        <img 
                            src="/react/flags/uk.png" alt="uk" 
                            style={{width: '20px', height: '12px', marginRight: '10px'}} />
                        Spanish
                    </Dropdown.Item>
                </Dropdown.Menu>
          </Dropdown>
          </div>
    )
}

