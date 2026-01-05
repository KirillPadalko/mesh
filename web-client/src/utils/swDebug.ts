/**
 * Service Worker Debug Utilities
 * Утилиты для отладки и управления Service Worker
 */

export const swDebug = {
    /**
     * Получить статус всех зарегистрированных service workers
     */
    async getStatus() {
        if (!('serviceWorker' in navigator)) {
            return { supported: false };
        }

        const registrations = await navigator.serviceWorker.getRegistrations();
        const controller = navigator.serviceWorker.controller;

        return {
            supported: true,
            controller: controller ? {
                scriptURL: controller.scriptURL,
                state: controller.state
            } : null,
            registrations: registrations.map(reg => ({
                scope: reg.scope,
                active: reg.active ? {
                    scriptURL: reg.active.scriptURL,
                    state: reg.active.state
                } : null,
                installing: reg.installing ? {
                    scriptURL: reg.installing.scriptURL,
                    state: reg.installing.state
                } : null,
                waiting: reg.waiting ? {
                    scriptURL: reg.waiting.scriptURL,
                    state: reg.waiting.state
                } : null
            }))
        };
    },

    /**
     * Удалить все зарегистрированные service workers
     */
    async unregisterAll() {
        if (!('serviceWorker' in navigator)) {
            console.warn('Service Workers not supported');
            return false;
        }

        const registrations = await navigator.serviceWorker.getRegistrations();
        const results = await Promise.all(
            registrations.map(reg => reg.unregister())
        );

        console.log(`Unregistered ${results.filter(r => r).length} service workers`);
        return results.every(r => r);
    },

    /**
     * Очистить весь кэш
     */
    async clearAllCaches() {
        if (!('caches' in window)) {
            console.warn('Cache API not supported');
            return false;
        }

        const cacheNames = await caches.keys();
        await Promise.all(
            cacheNames.map(name => caches.delete(name))
        );

        console.log(`Cleared ${cacheNames.length} caches:`, cacheNames);
        return true;
    },

    /**
     * Полная очистка: удалить SW и очистить кэш
     */
    async fullReset() {
        await this.clearAllCaches();
        await this.unregisterAll();
        console.log('Full SW reset completed. Please reload the page.');
        return true;
    },

    /**
     * Проверить, контролируется ли страница service worker
     */
    isControlled() {
        return navigator.serviceWorker.controller !== null;
    },

    /**
     * Обновить service worker принудительно
     */
    async forceUpdate() {
        if (!('serviceWorker' in navigator)) {
            return false;
        }

        const registrations = await navigator.serviceWorker.getRegistrations();
        await Promise.all(registrations.map(reg => reg.update()));
        console.log('Service worker update triggered');
        return true;
    },

    /**
     * Вывести debug информацию в консоль
     */
    async printDebugInfo() {
        const status = await this.getStatus();
        console.log('=== Service Worker Debug Info ===');
        console.log('Supported:', status.supported);
        console.log('Controlled:', this.isControlled());
        console.log('Controller:', status.controller);
        console.log('Registrations:', status.registrations);

        if ('caches' in window) {
            const cacheNames = await caches.keys();
            console.log('Cache names:', cacheNames);
        }
        console.log('=================================');
        return status;
    }
};

// Добавляем в window для доступа из консоли браузера
if (typeof window !== 'undefined') {
    (window as any).swDebug = swDebug;
}

export default swDebug;
