import { useState } from 'react';
import { identityManager } from '../../core/crypto/IdentityManager';
import './OnboardingFlow.css';

interface OnboardingFlowProps {
    onComplete: () => void;
}

export function OnboardingFlow({ onComplete }: OnboardingFlowProps) {
    const [step, setStep] = useState<'choice' | 'create' | 'restore'>('choice');
    const [nickname, setNickname] = useState('');
    const [mnemonic, setMnemonic] = useState('');
    const [generatedMnemonic, setGeneratedMnemonic] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleCreateIdentity = async () => {
        if (!nickname.trim()) {
            setError('Please enter a nickname');
            return;
        }

        setLoading(true);
        setError('');

        try {
            const mnemonic = await identityManager.createIdentity();
            identityManager.setLocalNickname(nickname);
            setGeneratedMnemonic(mnemonic);
        } catch (err) {
            setError('Failed to create identity: ' + err);
            setLoading(false);
        }
    };

    const handleRestoreIdentity = async () => {
        if (!nickname.trim() || !mnemonic.trim()) {
            setError('Please enter both nickname and mnemonic phrase');
            return;
        }

        setLoading(true);
        setError('');

        try {
            await identityManager.restoreFromMnemonic(mnemonic);
            identityManager.setLocalNickname(nickname);
            onComplete();
        } catch (err) {
            setError('Failed to restore identity: ' + err);
            setLoading(false);
        }
    };

    const handleFinishCreate = () => {
        onComplete();
    };

    if (step === 'choice') {
        return (
            <div className="onboarding-container">
                <div className="onboarding-card">
                    <h1>🕸️ Welcome to Mesh</h1>
                    <p className="subtitle">Decentralized P2P Messenger</p>

                    <div className="button-group">
                        <button onClick={() => setStep('create')} className="primary-button">
                            Create New Identity
                        </button>
                        <button onClick={() => setStep('restore')} className="secondary-button">
                            Restore from Mnemonic
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    if (step === 'create') {
        if (generatedMnemonic) {
            return (
                <div className="onboarding-container">
                    <div className="onboarding-card">
                        <h2>✅ Identity Created!</h2>
                        <div className="mnemonic-box">
                            <p><strong>Save your recovery phrase:</strong></p>
                            <div className="mnemonic">{generatedMnemonic}</div>
                            <p className="warning">⚠️ Write this down and keep it safe. You'll need it to restore your identity.</p>
                        </div>
                        <button onClick={handleFinishCreate} className="primary-button">
                            Continue to Mesh
                        </button>
                    </div>
                </div>
            );
        }

        return (
            <div className="onboarding-container">
                <div className="onboarding-card">
                    <h2>Create Identity</h2>

                    <div className="form-group">
                        <label>Nickname</label>
                        <input
                            type="text"
                            value={nickname}
                            onChange={(e) => setNickname(e.target.value)}
                            placeholder="Your name"
                            disabled={loading}
                        />
                    </div>

                    {error && <div className="error">{error}</div>}

                    <div className="button-group">
                        <button onClick={() => setStep('choice')} disabled={loading} className="secondary-button">
                            Back
                        </button>
                        <button onClick={handleCreateIdentity} disabled={loading} className="primary-button">
                            {loading ? 'Creating...' : 'Create'}
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    if (step === 'restore') {
        return (
            <div className="onboarding-container">
                <div className="onboarding-card">
                    <h2>Restore Identity</h2>

                    <div className="form-group">
                        <label>Nickname</label>
                        <input
                            type="text"
                            value={nickname}
                            onChange={(e) => setNickname(e.target.value)}
                            placeholder="Your name"
                            disabled={loading}
                        />
                    </div>

                    <div className="form-group">
                        <label>Recovery Phrase</label>
                        <textarea
                            value={mnemonic}
                            onChange={(e) => setMnemonic(e.target.value)}
                            placeholder="Enter your 12 or 24 word mnemonic phrase"
                            rows={4}
                            disabled={loading}
                        />
                    </div>

                    {error && <div className="error">{error}</div>}

                    <div className="button-group">
                        <button onClick={() => setStep('choice')} disabled={loading} className="secondary-button">
                            Back
                        </button>
                        <button onClick={handleRestoreIdentity} disabled={loading} className="primary-button">
                            {loading ? 'Restoring...' : 'Restore'}
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    return null;
}
