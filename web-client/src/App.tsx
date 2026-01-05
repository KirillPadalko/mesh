import { useEffect, useState } from 'react';
import { OnboardingFlow } from './features/identity/OnboardingFlow';
import { HomeScreen } from './ui/HomeScreen';
import { identityManager } from './core/crypto/IdentityManager';
import { InstallPrompt } from './pwa/InstallPromptComponent';
import { pwaInstallManager } from './pwa/PWAInstallManager';
import './index.css';

function App() {
    const [hasIdentity, setHasIdentity] = useState<boolean | null>(null);
    const [showInstallPrompt, setShowInstallPrompt] = useState(false);

    useEffect(() => {
        checkIdentity();
    }, []);

    const checkIdentity = async () => {
        // Check for invite in URL
        const params = new URLSearchParams(window.location.search);
        const inviteMeshId = params.get('invite');
        const inviteNickname = params.get('nickname');
        const showInstall = params.get('showInstall');

        if (inviteMeshId) {
            localStorage.setItem('meshPendingInvite', inviteMeshId);
            if (inviteNickname) {
                localStorage.setItem('meshPendingInviteNickname', inviteNickname);
            }
            // Clear URL
            window.history.replaceState({}, '', '/');
        }

        // Check if we should show install prompt
        // Show if: 1) Requested via URL, 2) Not already installed, 3) Not dismissed recently
        const dismissedTime = localStorage.getItem('meshInstallPromptDismissed');
        const shouldShowInstall = showInstall === 'true' || sessionStorage.getItem('meshRequestInstall') === 'true';
        const isInstalled = pwaInstallManager.isInstalled();
        const recentlyDismissed = dismissedTime && (Date.now() - parseInt(dismissedTime)) < 7 * 24 * 60 * 60 * 1000; // 7 days

        if (shouldShowInstall && !isInstalled && !recentlyDismissed) {
            // Small delay to let PWA events register
            setTimeout(() => {
                setShowInstallPrompt(true);
            }, 1000);
        }

        // Clear session flag
        sessionStorage.removeItem('meshRequestInstall');

        const exists = await identityManager.hasIdentity();
        setHasIdentity(exists);
    };

    const handleOnboardingComplete = () => {
        setHasIdentity(true);
    };

    const handleCloseInstallPrompt = () => {
        setShowInstallPrompt(false);
        localStorage.setItem('meshInstallPromptDismissed', Date.now().toString());
    };

    if (hasIdentity === null) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
                <div>Loading...</div>
            </div>
        );
    }

    if (!hasIdentity) {
        return <OnboardingFlow onComplete={handleOnboardingComplete} />;
    }

    return (
        <>
            <HomeScreen />
            {showInstallPrompt && <InstallPrompt onClose={handleCloseInstallPrompt} />}
        </>
    );
}

export default App;
