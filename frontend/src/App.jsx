import { IntlProvider } from 'react-intl';
import messagesEn from './locale/en.json';
import 'bootstrap/dist/css/bootstrap.min.css';
import NavBar from './components/AuthenticatedAppHeader';
import { QueryClient, QueryClientProvider } from 'react-query';
import FrontDesk from './FrontDesk';
import { BrowserRouter } from 'react-router-dom';

function App() {
  const messageMap = {
    en: messagesEn,
  };
  const locale = 'en';
  const containerStyle = {
    maxWidth: '1440px',
    marginLeft: 'auto',
    marginRight: 'auto',
    width: '100%',
    position: 'relative',
  }

  const queryClient = new QueryClient();

  return (
    <QueryClientProvider client={queryClient}>
    <div className="App" 
      style={containerStyle}
      >
      <BrowserRouter basename="/">
        <IntlProvider 
          locale="en"
          defaultLocale="en"
          messages={messageMap[locale]}
        >
          <FrontDesk adminUserInitialized={true} />
        </IntlProvider>
      </BrowserRouter>
    </div>
    </QueryClientProvider>
  );
}

export default App;
