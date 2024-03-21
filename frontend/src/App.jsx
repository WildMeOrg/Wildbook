import React, { useState } from 'react';
import { IntlProvider } from 'react-intl';
import messagesEn from './locale/en.json';
import messagesEs from './locale/es.json';
import messagesFr from './locale/fr.json';
import messagesIt from './locale/it.json';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';

import { QueryClient, QueryClientProvider } from 'react-query';
import FrontDesk from './FrontDesk';
import { BrowserRouter } from 'react-router-dom';
import LocaleContext from './IntlProvider';

function App() {
  const messageMap = {
    en: messagesEn,
    es: messagesEs,
    fr: messagesFr,
    it: messagesIt,
  };
  const [ locale, setLocale ] = useState('en');
  const containerStyle = {
    maxWidth: '1440px',
    marginLeft: 'auto',
    marginRight: 'auto',
    width: '100%',
    position: 'relative',
  }

  const queryClient = new QueryClient();

  const handleLocaleChange = (newLocale) => {
    console.log('handleLocaleChange', newLocale);
    setLocale(newLocale);
  }

  return (
    <QueryClientProvider client={queryClient}>
      <LocaleContext.Provider value={{locale, onLocaleChange: handleLocaleChange}}>
      <div className="App" 
        style={containerStyle}
        >
        <BrowserRouter basename="/react">
          <IntlProvider 
            locale={locale}
            defaultLocale="en"
            messages={messageMap[locale]}
          >
            <FrontDesk adminUserInitialized={true} setLocale={setLocale}/>
          </IntlProvider>
        </BrowserRouter>
      </div>
      </LocaleContext.Provider>
    </QueryClientProvider>
  );
}

export default App;
