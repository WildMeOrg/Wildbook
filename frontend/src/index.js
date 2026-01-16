import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import * as serviceWorkerRegistration from './serviceWorkerRegistration';
import reportWebVitals from './reportWebVitals';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// Service worker registration with auto-update on new versions
// When a new version is detected, it will automatically reload the page
serviceWorkerRegistration.register({
  onUpdate: (registration) => {
    console.log('New Wildbook version available!');
    // Skip waiting and take control immediately
    if (registration.waiting) {
      registration.waiting.postMessage({ type: 'SKIP_WAITING' });
    }
    // Reload the page to get the new version
    window.location.reload();
  },
  onSuccess: () => {
    console.log('Wildbook is ready for offline use.');
  },
});

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
