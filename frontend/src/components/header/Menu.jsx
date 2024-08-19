import React, { useState } from 'react';
import { Nav, NavDropdown } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import DownIcon from '../svg/DownIcon';
import RightIcon from '../svg/RightIcon';
import { authenticatedMenu } from '../../constants/navMenu';

export default function Menu({username}) {

    const [dropdownColor, setDropdownColor] = useState('transparent');
  
    const [dropdownShows, setDropdownShows] = useState({});
    const [dropdownBorder, setDropdownBorder] = useState('2px solid transparent');
  
    const handleMouseEnterLeave = (id, isEnter) => {
      setDropdownShows(prev => ({ ...prev, [id]: isEnter ? true : false }));
      setDropdownBorder(prev => ({ ...prev, [id]: isEnter ? '2px solid white' : '2px solid transparent' }));
    };

    return <>{authenticatedMenu(username).map((item, idx) => (
        <Nav className="me-auto" key={idx}>
          <NavDropdown
            className='header-dropdown'
            title={
              <span style={{ color: 'white' }}>
                <FormattedMessage id={Object.keys(item)[0].toUpperCase()} />
                {' '}
                <DownIcon color={'white'} />
              </span>}
            id={`basic-nav-dropdown${idx}`}
            style={{
              color: 'white', boxSizing: 'border-box',
              paddingLeft: 5,
              paddingRight: 5,
              borderBottom: dropdownBorder[`dropdown${idx + 1}`] || '2px solid transparent'
            }}
            onMouseEnter={() => handleMouseEnterLeave(`dropdown${idx + 1}`, true)}
            onMouseLeave={() => handleMouseEnterLeave(`dropdown${idx + 1}`, false)}
            show={dropdownShows[`dropdown${idx + 1}`]}
          >
            {
              Object.values(item)[0].map((subItem, idx) => {
                return (
                  subItem.sub
                    ? <NavDropdown
                      className='header-dropdown'
                      title={
                        <a
                          style={{
                            color: 'black',
                            fontSize: '0.9rem',
                            textDecoration: 'none'
                          }}
                          onClick={(e) => {
                            e.stopPropagation();
                            e.preventDefault();
                            window.location.href = subItem.href;
                          }}
                          href={subItem.href}>
                          {subItem.name}
                          <span style={{ paddingLeft: '34px' }}>
                            <RightIcon />
                          </span>
                        </a>

                      }
                      drop="end"
                      style={{
                        paddingLeft: 8,
                        fontSize: '0.9rem',
                        backgroundColor: dropdownColor,
                      }}
                      onMouseEnter={() => {
                        setDropdownColor('#CCF0FF');
                        handleMouseEnterLeave(`dropdown7`, true);
                      }}
                      onMouseLeave={() => {
                        setDropdownColor('white');
                        handleMouseEnterLeave(`dropdown7`, false);
                      }}
                      show={dropdownShows[`dropdown7`]}
                    >
                      {subItem.sub.map((sub) => {
                        return <NavDropdown.Item href={sub.href} style={{ color: 'black', fontSize: '0.9rem' }}>
                          {sub.name}
                        </NavDropdown.Item>
                      }
                      )}
                    </NavDropdown>
                    : <NavDropdown.Item href={subItem.href} style={{ color: 'black', fontSize: '0.9rem' }}>
                      {subItem.name}
                    </NavDropdown.Item>)
              })}
          </NavDropdown>
        </Nav>
      ))}
      </>


}