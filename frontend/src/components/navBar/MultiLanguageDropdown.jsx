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
                    <img src={`/react/flags/${flag}.png`} alt="flag" style={{width: '20px', height: '12px'}} />
                    <DownIcon />
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
                    {/* <Dropdown.Item onClick={() => onLocaleChange('en')}>
                        <img 
                            src="/react/flags/UK.png" alt="uk" 
                            style={{width: '20px', height: '12px', marginRight: '10px'}} />
                        English
                    </Dropdown.Item>
                    <Dropdown.Item onClick={() => onLocaleChange('es')}>
                        <img 
                            src="/react/flags/Spain.png" alt="spain" 
                            style={{width: '20px', height: '12px', marginRight: '10px'}} />
                        Español
                    </Dropdown.Item>
                    <Dropdown.Item onClick={() => onLocaleChange('fr')}>
                        <img 
                            src="/react/flags/France.png" alt="france" 
                            style={{width: '20px', height: '12px', marginRight: '10px'}} />
                        Francés
                    </Dropdown.Item>
                    <Dropdown.Item onClick={() => onLocaleChange('it')}>
                        <img 
                            src="/react/flags/Italy.png" alt="italy" 
                            style={{width: '20px', height: '12px', marginRight: '10px'}} />
                        Italiano
                    </Dropdown.Item> */}
                </Dropdown.Menu>
          </Dropdown>
          </div>
    )
}

