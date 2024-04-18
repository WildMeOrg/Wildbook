import React, { useContext, useState }from 'react';
import Dropdown from 'react-bootstrap/Dropdown';
import DownIcon from '../svg/DownIcon';
import LocaleContext from '../../IntlProvider';
import { locales, localeMap, languageMap } from '../../constants/locales';

export default function MultiLanguageDropdown() {

    const { onLocaleChange } = useContext(LocaleContext);
    const [ flag, setFlag ] = useState('UK');
    return (
        <div style={{
            backgroundColor: 'rgba(255, 255, 255, 0.25)',
            border: 'none',
            borderRadius: '30px',
            minWidth: '65px', 
            height: '35px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '10px',
        }}>
            
            <Dropdown style={{
                    border: 'none',
                    color: 'white',
                    display: 'flex',
                    flexDirection: 'row',
                    alignItems: 'center',
                    justifyContent: 'center',
                
                }}>
                <Dropdown.Toggle variant="basic" id="dropdown-basic" >
                    <img src={`/react/flags/${flag}.png`} alt="flag" style={{width: '20px', height: '12px'}} />
                    <span style={{paddingLeft: 7}}><DownIcon /></span>
                </Dropdown.Toggle>
                
                <Dropdown.Menu>
                    {
                        locales.map((locale, index) => (
                            <Dropdown.Item 
                                key={index} 
                                onClick={() => {
                                    onLocaleChange(locale);
                                    setFlag(localeMap[locale]);
                                }}>
                                <img 
                                    src={`/react/flags/${localeMap[locale]}.png`} alt={locale} 
                                    style={{width: '20px', height: '12px', marginRight: '10px'}} />
                                {languageMap[locale]}
                            </Dropdown.Item>
                        ))
                    }
                </Dropdown.Menu>
          </Dropdown>
          </div>
    )
}

