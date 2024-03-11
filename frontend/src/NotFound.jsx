import React from "react";
import Dropdown from 'react-bootstrap/Dropdown';
import DownIcon from './components/svg/DownIcon';


export default function NotFound() {
    return (
        <div style={{
            position: 'relative',
            zIndex: '200',
        }}>
            <img 
                src="/react/notFound.png" alt="notFound-forest"
                width='100%'
                className="vh-100"
                style={{
                    objectFit:'cover',
                    position:'absolute',
                    top:0,
                    left:0,
                }}
                              
                />
            <img 
                src="/react/Hedgehog.png" alt="notFound-hedgehog"                
                objectFit='cover'
                style={{
                    position:'absolute',
                    top:500,
                    left:400 
                }}                
                />


<Dropdown style={{
                    border: 'none',
                    color: 'white',
                    display: 'flex',
                    flexDirection: 'row',
                    alignItems: 'center',
                    justifyContent: 'center',
                
                }}>
                <Dropdown.Toggle variant="basic" id="dropdown-basic" >
                    <img src="/react/flags/uk.png" alt="uk" style={{width: '20px', height: '15px'}} />
                    <DownIcon />
                </Dropdown.Toggle>
                
                <Dropdown.Menu>
                <Dropdown.Item href="#/action-1"><img src="/react/flags/uk.png" alt="uk" style={{width: '30px', height: '30px'}} /></Dropdown.Item>
                <Dropdown.Item href="#/action-2"><img src="/react/flags/uk.png" alt="uk" style={{width: '30px', height: '30px'}} /></Dropdown.Item>
                <Dropdown.Item href="#/action-3"><img src="/react/flags/uk.png" alt="uk" style={{width: '30px', height: '30px'}} /></Dropdown.Item>
                </Dropdown.Menu>
          </Dropdown>
            
        </div>
    );
}