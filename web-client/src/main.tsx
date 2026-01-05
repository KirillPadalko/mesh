import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

import { registerSW } from './serviceWorkerRegistration';
import './utils/swDebug'; // Debug utilities available as window.swDebug

registerSW();

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>
);
