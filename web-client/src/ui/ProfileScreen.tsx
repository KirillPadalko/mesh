import { useState } from 'react';
import { identityManager } from '../core/crypto/IdentityManager';
import { meshGraphManager } from '../core/mesh/MeshGraphManager';
import { NotificationSettings } from './NotificationSettings';
import './ProfileScreen.css';

interface ProfileScreenProps {
    meshId: string;
    nickname: string;
    meshScore: number;
    onBack: () => void;
    onShowMap: () => void;
    onNicknameChange?: (newNick: string) => void;
}

export function ProfileScreen({ meshId, nickname, meshScore, onBack, onShowMap, onNicknameChange }: ProfileScreenProps) {
    const [showMnemonic, setShowMnemonic] = useState(false);
    const [mnemonic, setMnemonic] = useState('');
    const [isEditingNick, setIsEditingNick] = useState(false);
    const [editedNick, setEditedNick] = useState(nickname);
    const [showNotificationSettings, setShowNotificationSettings] = useState(false);

    const handleExportMnemonic = () => {
        const exported = identityManager.exportMnemonic();
        if (exported) {
            setMnemonic(exported);
            setShowMnemonic(true);
        }
    };

    const handleSaveNick = () => {
        if (editedNick.trim()) {
            identityManager.setLocalNickname(editedNick.trim());
            setIsEditingNick(false);
            onNicknameChange?.(editedNick.trim());
        }
    };

    const handleCopyMeshId = () => {
        navigator.clipboard.writeText(meshId);
        alert('Mesh ID copied to clipboard!');
    };

    const { l1, l2 } = meshGraphManager.getMeshScoreDetails();

    return (
        <div className="profile-container">
            <div className="profile-header">
                <button onClick={onBack} className="back-btn">Back</button>
                <h2>Profile</h2>
            </div>

            <div className="profile-content">
                <div className="profile-section">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <h3>Identity</h3>

                    </div>
                    <div className="info-item">
                        <label>Nickname</label>
                        {isEditingNick ? (
                            <div className="nick-edit-row" style={{ display: 'flex', gap: '10px' }}>
                                <input
                                    type="text"
                                    value={editedNick}
                                    onChange={(e) => setEditedNick(e.target.value)}
                                    className="nick-input"
                                    autoFocus
                                    onKeyPress={(e) => e.key === 'Enter' && handleSaveNick()}
                                    style={{ flex: 1, padding: '5px', borderRadius: '4px', border: '1px solid #334155', background: '#1e293b', color: 'white' }}
                                />
                                <button onClick={handleSaveNick} className="save-nick-btn" style={{ padding: '5px 10px', background: '#38bdf8', color: '#0f172a', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>Save</button>
                                <button onClick={() => setIsEditingNick(false)} className="cancel-nick-btn" style={{ padding: '5px 10px', background: '#475569', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>Cancel</button>
                            </div>
                        ) : (
                            <div className="nick-display-row" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div className="info-value" style={{ fontSize: '1.2rem', fontWeight: 'bold' }}>{nickname}</div>
                                <button onClick={() => setIsEditingNick(true)} className="edit-nick-btn" style={{ background: 'none', border: 'none', color: '#38bdf8', cursor: 'pointer' }}>Edit</button>
                            </div>
                        )}
                    </div>
                    <div className="info-item">
                        <label>Mesh ID</label>
                        <div className="mesh-id-display">
                            <code className="mesh-id">{meshId}</code>
                            <button onClick={handleCopyMeshId} className="copy-btn">Copy</button>
                        </div>
                    </div>
                </div>

                <div className="profile-section">
                    <h3>Network Stats</h3>
                    <div className="stats-grid">
                        <div className="stat-item">
                            <div className="stat-value">{meshScore.toFixed(1)}</div>
                            <div className="stat-label">Mesh Score</div>
                        </div>
                        <div className="stat-item">
                            <div className="stat-value">{l1}</div>
                            <div className="stat-label">L1 Connections</div>
                        </div>
                        <div className="stat-item">
                            <div className="stat-value">{l2}</div>
                            <div className="stat-label">L2 Connections</div>
                        </div>
                    </div>
                    <button onClick={onShowMap} className="view-map-btn">
                        View Network Map
                    </button>
                </div>

                <div className="profile-section">
                    <h3>Уведомления</h3>
                    <p style={{ color: 'var(--text-secondary)', marginBottom: '16px', fontSize: '14px' }}>
                        Получайте уведомления о новых сообщениях и событиях
                    </p>
                    <button
                        onClick={() => setShowNotificationSettings(true)}
                        className="notification-settings-btn"
                    >
                        🔔 Настройки уведомлений
                    </button>
                </div>

                <div className="profile-section">
                    <h3>Backup</h3>
                    <button onClick={handleExportMnemonic} className="export-btn">
                        Export Recovery Phrase
                    </button>

                    {showMnemonic && (
                        <div className="mnemonic-display">
                            <div className="warning-box">
                                Keep this phrase safe! Anyone with this phrase can access your identity.
                            </div>
                            <div className="mnemonic-text">{mnemonic}</div>
                        </div>
                    )}
                </div>
            </div>

            {showNotificationSettings && (
                <NotificationSettings onClose={() => setShowNotificationSettings(false)} />
            )}
        </div>
    );
}
