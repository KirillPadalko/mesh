/**
 * PWA Install Prompt Manager
 * Handles the browser's native PWA install prompt
 */

interface BeforeInstallPromptEvent extends Event {
    prompt(): Promise<void>;
    userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

class PWAInstallManager {
    private deferredPrompt: BeforeInstallPromptEvent | null = null;
    private isInstallable = false;

    constructor() {
        this.init();
    }

    private init() {
        // Listen for the beforeinstallprompt event
        window.addEventListener('beforeinstallprompt', (e) => {
            e.preventDefault();
            this.deferredPrompt = e as BeforeInstallPromptEvent;
            this.isInstallable = true;
            console.log('PWA: Install prompt is available');
        });

        // Check if already installed
        window.addEventListener('appinstalled', () => {
            console.log('PWA: App installed successfully');
            this.deferredPrompt = null;
            this.isInstallable = false;
        });

        // Check if running as installed PWA
        if (window.matchMedia('(display-mode: standalone)').matches) {
            console.log('PWA: Running as installed app');
        }
    }

    /**
     * Show the native install prompt if available
     * @returns Promise<boolean> - true if user accepted, false if dismissed or not available
     */
    async showInstallPrompt(): Promise<boolean> {
        if (!this.deferredPrompt) {
            console.log('PWA: Install prompt not available');
            return false;
        }

        try {
            // Show the install prompt
            await this.deferredPrompt.prompt();

            // Wait for the user to respond
            const { outcome } = await this.deferredPrompt.userChoice;

            console.log(`PWA: User ${outcome} the install prompt`);

            // Clear the prompt
            this.deferredPrompt = null;
            this.isInstallable = false;

            return outcome === 'accepted';
        } catch (error) {
            console.error('PWA: Error showing install prompt:', error);
            return false;
        }
    }

    /**
     * Check if the install prompt is available
     */
    canInstall(): boolean {
        return this.isInstallable;
    }

    /**
     * Check if app is installed
     */
    isInstalled(): boolean {
        return window.matchMedia('(display-mode: standalone)').matches;
    }

    /**
     * Get installation instructions based on browser/platform
     */
    getInstallInstructions(): { platform: string; instructions: string } {
        const userAgent = navigator.userAgent.toLowerCase();

        // iOS Safari
        if (/iphone|ipad|ipod/.test(userAgent) && /safari/.test(userAgent) && !/chrome|crios|fxios/.test(userAgent)) {
            return {
                platform: 'iOS Safari',
                instructions: 'Tap the Share button, then tap "Add to Home Screen"'
            };
        }

        // Android Chrome
        if (/android/.test(userAgent) && /chrome/.test(userAgent)) {
            return {
                platform: 'Android Chrome',
                instructions: 'Tap the menu (⋮), then tap "Add to Home screen"'
            };
        }

        // Desktop Chrome/Edge
        if (/chrome|edg/.test(userAgent) && !((/android/.test(userAgent)))) {
            return {
                platform: 'Desktop Chrome/Edge',
                instructions: 'Click the install icon in the address bar, or open menu → "Install Mesh"'
            };
        }

        // Firefox
        if (/firefox/.test(userAgent)) {
            return {
                platform: 'Firefox',
                instructions: 'Click the home icon in the address bar'
            };
        }

        // Default
        return {
            platform: 'Browser',
            instructions: 'Look for an "Install" or "Add to Home Screen" option in your browser menu'
        };
    }
}

// Create singleton instance
export const pwaInstallManager = new PWAInstallManager();
