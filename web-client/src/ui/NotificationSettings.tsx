import { useState, useEffect } from 'react';
import { notificationManager } from '../utils/NotificationManager';
import './NotificationSettings.css';

interface NotificationSettingsProps {
    onClose: () => void;
}

export function NotificationSettings({ onClose }: NotificationSettingsProps) {
    const [permission, setPermission] = useState<NotificationPermission>('default');

    useEffect(() => {
        updateStatus();
    }, []);

    const updateStatus = () => {
        if ('Notification' in window) {
            setPermission(Notification.permission);
        }
    };

    const handleEnableNotifications = async () => {
        const granted = await notificationManager.requestPermission();
        updateStatus();

        if (granted) {
            // Show test notification
            await notificationManager.showNotification('Уведомления включены', {
                body: 'Вы будете получать уведомления о новых сообщениях',
                icon: '/logo.png',
            });
        }
    };

    const handleTestNotification = async () => {
        await notificationManager.showNotification('Тестовое уведомление', {
            body: 'Это тестовое уведомление от Mesh',
            icon: '/logo.png',
        });
    };

    return (
        <div className="notification-settings-overlay" onClick={onClose}>
            <div className="notification-settings-modal" onClick={(e) => e.stopPropagation()}>
                <div className="notification-settings-header">
                    <h2>Настройки уведомлений</h2>
                    <button className="close-btn" onClick={onClose}>×</button>
                </div>

                <div className="notification-settings-content">
                    <div className="setting-item">
                        <div className="setting-info">
                            <h3>Статус уведомлений</h3>
                            <p className={`status ${permission}`}>
                                {permission === 'granted' && '✓ Включены'}
                                {permission === 'denied' && '✗ Заблокированы'}
                                {permission === 'default' && '○ Не настроены'}
                            </p>
                        </div>
                    </div>

                    {permission === 'denied' && (
                        <div className="warning-box">
                            <p>
                                Уведомления заблокированы в настройках браузера.
                                Чтобы включить их, разрешите уведомления для этого сайта
                                в настройках вашего браузера.
                            </p>
                        </div>
                    )}

                    {permission === 'default' && (
                        <div className="info-box">
                            <p>
                                Включите уведомления, чтобы получать оповещения о новых сообщениях,
                                даже когда приложение не активно.
                            </p>
                        </div>
                    )}

                    <div className="notification-features">
                        <h3>Что вы будете получать:</h3>
                        <ul>
                            <li>✉️ Новые сообщения от контактов</li>
                            <li>👥 Уведомления о новых контактах</li>
                            <li>🔗 Обновления о L2 соединениях</li>
                        </ul>
                    </div>

                    <div className="settings-actions">
                        {permission !== 'granted' && (
                            <button
                                className="enable-btn"
                                onClick={handleEnableNotifications}
                            >
                                Включить уведомления
                            </button>
                        )}

                        {permission === 'granted' && (
                            <button
                                className="test-btn"
                                onClick={handleTestNotification}
                            >
                                Отправить тестовое уведомление
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
