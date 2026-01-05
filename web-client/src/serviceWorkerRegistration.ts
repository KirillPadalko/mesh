export function registerSW() {
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker
                .register('/sw.js')
                .then((registration) => {
                    console.log('SW registered: ', registration);

                    // Check for updates periodically
                    registration.addEventListener('updatefound', () => {
                        const newWorker = registration.installing;
                        console.log('SW update found');

                        if (newWorker) {
                            newWorker.addEventListener('statechange', () => {
                                console.log('SW state changed:', newWorker.state);
                            });
                        }
                    });

                    // Check for updates every hour
                    setInterval(() => {
                        registration.update();
                    }, 60 * 60 * 1000);
                })
                .catch((registrationError) => {
                    console.error('SW registration failed: ', registrationError);
                });
        });

        // Handle controller change
        navigator.serviceWorker.addEventListener('controllerchange', () => {
            console.log('SW controller changed');
        });
    }
}
