/**
 * NotificationManager - manages browser notifications for the Mesh app
 */
export class NotificationManager {
    private static instance: NotificationManager;
    private permission: NotificationPermission = 'default';
    private isWindowFocused = true;

    private constructor() {
        // Check initial permission
        if ('Notification' in window) {
            this.permission = Notification.permission;
        }

        // Track window focus to avoid showing notifications when app is active
        window.addEventListener('focus', () => {
            this.isWindowFocused = true;
        });

        window.addEventListener('blur', () => {
            this.isWindowFocused = false;
        });
    }

    static getInstance(): NotificationManager {
        if (!NotificationManager.instance) {
            NotificationManager.instance = new NotificationManager();
        }
        return NotificationManager.instance;
    }

    /**
     * Request notification permission from the user
     */
    async requestPermission(): Promise<boolean> {
        if (!('Notification' in window)) {
            console.warn('This browser does not support notifications');
            return false;
        }

        if (this.permission === 'granted') {
            return true;
        }

        try {
            this.permission = await Notification.requestPermission();
            return this.permission === 'granted';
        } catch (error) {
            console.error('Error requesting notification permission:', error);
            return false;
        }
    }

    /**
     * Check if notifications are enabled
     */
    isEnabled(): boolean {
        return this.permission === 'granted';
    }

    /**
     * Show a notification (uses Service Worker if available, fallback to regular notification)
     */
    async showNotification(title: string, options?: NotificationOptions): Promise<void> {
        if (this.permission !== 'granted') {
            console.warn('Notification permission not granted');
            return;
        }

        // Don't show notification if window is focused and user is active
        if (this.isWindowFocused && document.visibilityState === 'visible') {
            console.log('Window is focused, skipping notification');
            return;
        }

        try {
            // Try to use Service Worker for better notification management
            if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
                const registration = await navigator.serviceWorker.ready;
                await registration.showNotification(title, {
                    ...options,
                    badge: '/logo.png',
                    vibrate: [200, 100, 200],
                } as any); // Cast to any as vibrate is valid but not in TypeScript types
            } else {
                // Fallback to regular notification
                new Notification(title, options);
            }
        } catch (error) {
            console.error('Error showing notification:', error);
        }
    }

    /**
     * Show notification for a new message
     */
    async notifyNewMessage(fromNickname: string, messageText: string, fromMeshId: string): Promise<void> {
        const title = `Новое сообщение от ${fromNickname}`;
        const body = messageText.length > 100
            ? messageText.substring(0, 100) + '...'
            : messageText;

        await this.showNotification(title, {
            body,
            icon: '/logo.png',
            tag: `message-${fromMeshId}`, // Replaces previous notification from same contact
            requireInteraction: false,
            data: {
                type: 'new-message',
                fromMeshId,
                timestamp: Date.now(),
            },
        });
    }

    /**
     * Show notification for a new contact
     */
    async notifyNewContact(contactNickname: string, contactMeshId: string): Promise<void> {
        const title = 'Новый контакт';
        const body = `${contactNickname} добавлен в ваши контакты`;

        await this.showNotification(title, {
            body,
            icon: '/logo.png',
            tag: `contact-${contactMeshId}`,
            requireInteraction: false,
            data: {
                type: 'new-contact',
                contactMeshId,
                timestamp: Date.now(),
            },
        });
    }

    /**
     * Show notification for L2 connection
     */
    async notifyL2Connection(viaContact: string): Promise<void> {
        const title = 'Новое подключение';
        const body = `Обнаружено L2 соединение через ${viaContact}`;

        await this.showNotification(title, {
            body,
            icon: '/logo.png',
            tag: 'l2-connection',
            requireInteraction: false,
            data: {
                type: 'l2-connection',
                timestamp: Date.now(),
            },
        });
    }
}

// Export singleton instance
export const notificationManager = NotificationManager.getInstance();
