import React, { useEffect, useState } from 'react';
import { pwaInstallManager } from './PWAInstallManager';
import './InstallPrompt.css';

interface InstallPromptProps {
    onClose?: () => void;
}

export const InstallPrompt: React.FC<InstallPromptProps> = ({ onClose }) => {
    const [canInstall, setCanInstall] = useState(false);
    const [showInstructions, setShowInstructions] = useState(false);
    const [installing, setInstalling] = useState(false);

    useEffect(() => {
        // Small delay to let the prompt event register
        const timer = setTimeout(() => {
            setCanInstall(pwaInstallManager.canInstall());
        }, 500);

        return () => clearTimeout(timer);
    }, []);

    const handleInstall = async () => {
        setInstalling(true);
        const accepted = await pwaInstallManager.showInstallPrompt();

        if (accepted) {
            console.log('User accepted installation');
            onClose?.();
        } else {
            // Show manual instructions if prompt was dismissed or unavailable
            setShowInstructions(true);
            setInstalling(false);
        }
    };

    const handleShowInstructions = () => {
        setShowInstructions(true);
    };

    const instructions = pwaInstallManager.getInstallInstructions();

    if (!canInstall && !showInstructions) {
        // Show manual instructions immediately if native prompt not available
        return (
            <div className="install-prompt">
                <div className="install-prompt-content">
                    <div className="install-prompt-header">
                        <h3>📱 Install Mesh</h3>
                        <button className="install-prompt-close" onClick={onClose}>×</button>
                    </div>

                    <p className="install-prompt-message">
                        Add Mesh to your home screen for a better experience!
                    </p>

                    <div className="install-instructions">
                        <div className="install-platform">{instructions.platform}</div>
                        <p>{instructions.instructions}</p>
                    </div>

                    <button className="install-prompt-btn secondary" onClick={onClose}>
                        Maybe Later
                    </button>
                </div>
            </div>
        );
    }

    if (showInstructions) {
        return (
            <div className="install-prompt">
                <div className="install-prompt-content">
                    <div className="install-prompt-header">
                        <h3>📱 How to Install</h3>
                        <button className="install-prompt-close" onClick={onClose}>×</button>
                    </div>

                    <div className="install-instructions">
                        <div className="install-platform">{instructions.platform}</div>
                        <p>{instructions.instructions}</p>
                    </div>

                    <button className="install-prompt-btn secondary" onClick={onClose}>
                        Got it!
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="install-prompt">
            <div className="install-prompt-content">
                <div className="install-prompt-header">
                    <h3>📱 Install Mesh</h3>
                    <button className="install-prompt-close" onClick={onClose}>×</button>
                </div>

                <p className="install-prompt-message">
                    Get quick access to Mesh by adding it to your home screen!
                </p>

                <div className="install-prompt-features">
                    <div className="feature-item">✓ Launch like a native app</div>
                    <div className="feature-item">✓ Works offline</div>
                    <div className="feature-item">✓ Fast and convenient</div>
                </div>

                <button
                    className="install-prompt-btn primary"
                    onClick={handleInstall}
                    disabled={installing}
                >
                    {installing ? 'Installing...' : 'Install Now'}
                </button>

                <button className="install-prompt-btn secondary" onClick={handleShowInstructions}>
                    Show Manual Instructions
                </button>

                <button className="install-prompt-btn tertiary" onClick={onClose}>
                    Maybe Later
                </button>
            </div>
        </div>
    );
};
