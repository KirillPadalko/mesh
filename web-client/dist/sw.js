const CACHE_NAME = 'mesh-cache-v2';
const ASSETS_TO_CACHE = [
    '/',
    '/index.html',
    '/logo.png',
    '/manifest.json'
];

// Install event: Cache core assets
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            console.log('Opened cache');
            return cache.addAll(ASSETS_TO_CACHE);
        })
    );
});

// Activate event: Clean up old caches
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((cacheNames) => {
            return Promise.all(
                cacheNames.map((cacheName) => {
                    if (cacheName !== CACHE_NAME) {
                        console.log('Deleting old cache:', cacheName);
                        return caches.delete(cacheName);
                    }
                })
            );
        })
    );
});

// Fetch event: Cache First for static assets, Network First for others
self.addEventListener('fetch', (event) => {
    // Skip cross-origin requests
    if (!event.request.url.startsWith(self.location.origin)) {
        return;
    }

    // Skip non-GET requests
    if (event.request.method !== 'GET') {
        return;
    }

    // API/WebSocket requests should generally go to network, but here we only intercept HTTP
    // For this simple PWA, we'll try cache first, then network
    event.respondWith(
        caches.match(event.request)
            .then((response) => {
                // Cache hit - return response
                if (response) {
                    return response;
                }

                return fetch(event.request).then((response) => {
                    // Check if we received a valid response
                    if (!response || response.status !== 200 || response.type !== 'basic') {
                        return response;
                    }

                    // Clone the response
                    const responseToCache = response.clone();

                    // Cache in background (don't wait for it)
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(event.request, responseToCache);
                    }).catch((err) => {
                        console.warn('Failed to cache:', err);
                    });

                    return response;
                });
            })
            .catch((error) => {
                console.error('Fetch failed:', error);
                // Return a fallback or throw the error
                throw error;
            })
    );
});

// Message handler: Properly handle messages from clients
self.addEventListener('message', (event) => {
    console.log('SW received message:', event.data);

    // Handle specific message types
    if (event.data && event.data.type === 'SKIP_WAITING') {
        self.skipWaiting();
        // Don't return true, just send response if needed
        if (event.ports && event.ports[0]) {
            event.ports[0].postMessage({ success: true });
        }
        return;
    }

    // For other messages, you can add handlers here
    // IMPORTANT: Only return true if you're going to send a response asynchronously
    // Otherwise, don't return anything or return false
});

// Notification click handler: Open/focus the app when notification is clicked
self.addEventListener('notificationclick', (event) => {
    console.log('Notification clicked:', event.notification.tag);
    event.notification.close();

    // Get notification data
    const data = event.notification.data || {};
    const { type, fromMeshId, contactMeshId } = data;

    // Determine URL to open
    let urlToOpen = '/';

    // You can add more sophisticated routing here if needed
    // For now, just open the main app

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
            // Check if there's already a window open
            for (let i = 0; i < clientList.length; i++) {
                const client = clientList[i];
                if (client.url.includes(self.location.origin) && 'focus' in client) {
                    return client.focus();
                }
            }

            // If no window is open, open a new one
            if (clients.openWindow) {
                return clients.openWindow(urlToOpen);
            }
        })
    );
});
