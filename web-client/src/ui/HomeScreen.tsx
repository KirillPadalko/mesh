import { useState, useEffect, useRef } from 'react';
import { identityManager } from '../core/crypto/IdentityManager';
import { CryptoManager } from '../core/crypto/CryptoManager';
import { meshGraphManager } from '../core/mesh/MeshGraphManager';
import { WebSocketService } from '../core/network/WebSocketService';
import { WebRTCManager } from '../core/network/WebRTCManager';
import { ChatTransport } from '../core/network/ChatTransport';
import { InviteManager } from '../features/invite/InviteManager';
import { db } from '../core/storage/StorageManager';
import { Contact } from '../types';
import { ChatScreen } from './ChatScreen';
import { ProfileScreen } from './ProfileScreen';
import { NetworkMap } from './NetworkMap';
import { notificationManager } from '../utils/NotificationManager';
import './HomeScreen.css';
import logo from '../assets/logo.png';

export function HomeScreen() {
    const [meshId, setMeshId] = useState('');
    const [nickname, setNickname] = useState('');
    const [meshScore, setMeshScore] = useState(0);
    const [contacts, setContacts] = useState<Contact[]>([]);
    const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
    const [view, setView] = useState<'contacts' | 'chat' | 'profile' | 'network'>('contacts');
    const selectedContactRef = useRef<Contact | null>(null);

    useEffect(() => {
        selectedContactRef.current = selectedContact;
    }, [selectedContact]);
    const [transport, setTransport] = useState<ChatTransport | null>(null);
    const [inviteManager, setInviteManager] = useState<InviteManager | null>(null);
    const [notification, setNotification] = useState<string | null>(null);
    const [, setInviteLink] = useState('');

    // Helper functions moved up for scope access
    const loadContacts = async () => {
        const loaded = await db.getContactsWithPreview();
        setContacts(loaded);
    };

    const ensureContact = async (meshId: string, nickname?: string) => {
        const existing = await db.getContact(meshId);
        if (!existing) {
            await db.insertContact({
                meshId,
                nickname: nickname || `User ${meshId.substring(0, 4)}`,
            });
        } else if (nickname && existing.nickname !== nickname) {
            await db.insertContact({
                ...existing,
                nickname,
            });
        }
    };

    const handleGraphChange = async () => {
        const newScore = meshGraphManager.getMeshScore();
        console.debug(`[HomeScreen] handleGraphChange called, updating meshScore to ${newScore}`);
        setMeshScore(newScore);
        await loadContacts();
    };

    // Refs to hold services across renders without triggering re-renders
    // Using refs strictly for cleanup purposes
    const servicesRef = useState<{
        wsService: WebSocketService | null,
        transport: ChatTransport | null
    }>({ wsService: null, transport: null })[0];

    useEffect(() => {
        let isMounted = true;

        // Define event handlers
        const handleL2Update = (data: any) => {
            const viaContact = data.via?.substring(0, 8) || 'unknown';
            setNotification(`New L2 Connection discovered via ${data.via.substring(0, 4)}!`);
            notificationManager.notifyL2Connection(viaContact);
            handleGraphChange();
            setTimeout(() => setNotification(null), 5000);
        };

        const handleContactUpdate = () => {
            console.debug('[HomeScreen] contact-update event received');
            handleGraphChange();
        };

        const initApp = async () => {
            try {
                const id = await identityManager.getMeshId();
                const nick = identityManager.getLocalNickname();
                const score = meshGraphManager.getMeshScore();

                if (!isMounted) return;

                setMeshId(id || '');
                setNickname(nick);
                setMeshScore(score);

                // Subscribe to Mesh Graph events
                meshGraphManager.on('l2-update', handleL2Update);
                meshGraphManager.on('contact-update', handleContactUpdate);

                // Request notification permission
                await notificationManager.requestPermission();

                if (id) {
                    // Initialize network stack
                    // Check if we already have services running to avoid duplicates
                    if (servicesRef.wsService) {
                        console.warn('Network stack already initialized, skipping...');
                        return;
                    }

                    const cryptoManager = new CryptoManager(identityManager);
                    const wsService = new WebSocketService();
                    const webRTC = new WebRTCManager(wsService);
                    const chatTransport = new ChatTransport(cryptoManager, webRTC, wsService);
                    const inviteMgr = new InviteManager(identityManager, cryptoManager, meshGraphManager);

                    // Store refs
                    servicesRef.wsService = wsService;
                    servicesRef.transport = chatTransport;

                    setTransport(chatTransport);
                    setInviteManager(inviteMgr);

                    // Set up listeners
                    chatTransport.listener = {
                        onMessageReceived: async (fromMeshId, text, timestamp) => {
                            await ensureContact(fromMeshId);
                            const contact = await db.getContact(fromMeshId);
                            const nickname = contact?.nickname || `User ${fromMeshId.substring(0, 4)}`;

                            await db.insertMessage({
                                peerId: fromMeshId,
                                isIncoming: true,
                                text,
                                timestamp,
                                status: 'received',
                            });

                            await notificationManager.notifyNewMessage(nickname, text, fromMeshId);

                            if (selectedContactRef.current?.meshId !== fromMeshId) {
                                setNotification(`New message from ${nickname}`);
                                setTimeout(() => setNotification(null), 3000);
                            }
                            loadContacts();
                        },

                        onMessageStatusChanged: (peerId, isP2P) => {
                            console.log(`Connection status to ${peerId}: ${isP2P ? 'P2P' : 'Server'}`);
                        },

                        onInviteReceived: async (fromMeshId, inviteJson) => {
                            console.log('Invite Recieved from', fromMeshId);
                            // Only process if we have the manager ready
                            try {
                                const invite = JSON.parse(inviteJson);
                                const ack = await inviteMgr.processInvite(invite); // Use local var to be safe

                                if (ack) {
                                    const contactNickname = invite.nickname || `User ${fromMeshId.substring(0, 4)}`;

                                    // Add to contacts
                                    await ensureContact(fromMeshId, contactNickname);

                                    // Show notification
                                    await notificationManager.notifyNewContact(contactNickname, fromMeshId);
                                    setNotification(`New contact added: ${contactNickname}`);
                                    setTimeout(() => setNotification(null), 4000);

                                    // Send ACK
                                    const ackJson = JSON.stringify(ack);
                                    chatTransport.sendInviteAck(fromMeshId, ackJson);

                                    loadContacts();
                                    setMeshScore(meshGraphManager.getMeshScore());
                                }
                            } catch (e) {
                                console.error('Error processing invite:', e);
                            }
                        },

                        onInviteAckReceived: async (fromMeshId, ackJson) => {
                            console.log('Invite ACK Recieved from', fromMeshId);
                            try {
                                const ack = JSON.parse(ackJson);
                                const valid = await inviteMgr.processInviteAck(ack);

                                if (valid) {
                                    await ensureContact(fromMeshId, ack.nickname);
                                    loadContacts();

                                    // Notify user
                                    const name = ack.nickname || fromMeshId.substring(0, 4);
                                    setNotification(`${name} accepted your invite!`);
                                    setTimeout(() => setNotification(null), 4000);

                                    await notificationManager.notifyNewContact(name, fromMeshId);
                                }
                            } catch (e) {
                                console.error('Error processing invite ACK:', e);
                            }
                        },

                        onL2NotifyReceived: (fromMeshId, _notifyJson) => {
                            console.log('L2 notify received from:', fromMeshId);
                        },

                        onTransportError: (message) => {
                            console.error('Transport error:', message);
                            // Avoid spamming notifications for 'offline' if not critical
                            if (message.includes('offline')) return;

                            setNotification(message);
                            setTimeout(() => setNotification(null), 5000);
                        },
                    };

                    console.log('Connecting to WS...');
                    wsService.connect(id);

                    // Sync contacts from Graph to DB
                    const l1Connections = meshGraphManager.getL1Connections();
                    for (const peerId of l1Connections) {
                        const nick = meshGraphManager.getNickname(peerId); // Get nick from graph if possible
                        await ensureContact(peerId, nick);
                    }

                    // Initial load
                    await loadContacts();

                    // Handle pending invite
                    const pendingInvite = localStorage.getItem('meshPendingInvite');
                    const pendingInviteNickname = localStorage.getItem('meshPendingInviteNickname');

                    if (pendingInvite && pendingInvite !== id) {
                        console.log('Found pending invite to', pendingInvite);
                        localStorage.removeItem('meshPendingInvite');
                        localStorage.removeItem('meshPendingInviteNickname');

                        meshGraphManager.addL1Connection(pendingInvite);
                        await ensureContact(pendingInvite, pendingInviteNickname || undefined);

                        const inviteJson = JSON.stringify(await inviteMgr.createInvite(pendingInvite));
                        chatTransport.sendInvite(pendingInvite, inviteJson);
                    }
                }
            } catch (error) {
                console.error("Initialization failed:", error);
            }
        };

        initApp();

        // Cleanup
        return () => {
            isMounted = false;
            console.log('HomeScreen unmounting, cleaning up...');

            meshGraphManager.off('l2-update', handleL2Update);
            meshGraphManager.off('contact-update', handleContactUpdate);

            if (servicesRef.wsService) {
                servicesRef.wsService.disconnect();
                servicesRef.wsService = null;
                servicesRef.transport = null;
            }
        };
    }, []);




    const handleSelectContact = (contact: Contact) => {
        setSelectedContact(contact);
        setView('chat');
    };



    const handleAddContactById = async () => {
        const targetId = window.prompt('Enter Mesh ID to add:');
        if (!targetId || targetId.trim().length < 10) return;

        const cleanId = targetId.trim();

        try {
            // 1. Add to graph
            meshGraphManager.addL1Connection(cleanId);

            // 2. Add to DB
            await ensureContact(cleanId);

            // 3. Send actual invite if transport is ready
            if (transport && inviteManager) {
                const inviteJson = JSON.stringify(await inviteManager.createInvite(cleanId));
                transport.sendInvite(cleanId, inviteJson);
                setNotification(`Sent invite to ${cleanId.substring(0, 8)}...`);
            } else {
                setNotification(`Added ${cleanId.substring(0, 8)}... to contacts`);
            }

            await loadContacts();
            setMeshScore(meshGraphManager.getMeshScore());
            setTimeout(() => setNotification(null), 3000);
        } catch (e) {
            console.error('Failed to add contact manually:', e);
            alert('Failed to add contact. Please check the ID.');
        }
    };

    const handleSendInvite = async () => {
        const myMeshId = await identityManager.getMeshId();
        if (!myMeshId) return;

        // Get nickname for the invite
        const myNickname = identityManager.getLocalNickname();

        // Use production domain instead of IP address
        const baseUrl = `https://mesh-online.org/invite/${myMeshId}`;
        const link = myNickname
            ? `${baseUrl}?nickname=${encodeURIComponent(myNickname)}`
            : baseUrl;

        setInviteLink(link);

        // Copy to clipboard
        try {
            await navigator.clipboard.writeText(link);
            const displayName = myNickname || "you";
            setNotification(`Invite link copied! Share it so people can connect with ${displayName}`);
        } catch (err) {
            console.error('Failed to copy invite:', err);
            setNotification("Failed to copy invite link");
        }
    };

    const getSignalLevel = (score: number): number => {
        if (score >= 50) return 5;
        if (score >= 25) return 4;
        if (score >= 10) return 3;
        if (score >= 3) return 2;
        if (score >= 1) return 1;
        return 0;
    };

    if (view === 'chat' && selectedContact) {
        return (
            <ChatScreen
                contact={selectedContact}
                transport={transport}
                onBack={() => {
                    setView('contacts');
                    setSelectedContact(null);
                    loadContacts();
                }}
            />
        );
    }

    if (view === 'profile') {
        return (
            <ProfileScreen
                meshId={meshId}
                nickname={nickname}
                meshScore={meshScore}
                onBack={() => setView('contacts')}
                onShowMap={() => setView('network')}
                onNicknameChange={(newNick) => setNickname(newNick)}
            />
        );
    }

    if (view === 'network') {
        return <NetworkMap onBack={() => setView('contacts')} />;
    }

    return (
        <div className="home-container">
            <div className="header">
                <div className="header-left">
                    <div className="logo-container">
                        <img src={logo} alt="Mesh" className="app-logo" />
                        <h1>Mesh</h1>
                    </div>
                    <div className="mesh-signal">
                        <div className="signal-dots">
                            {Array.from({ length: 5 }).map((_, i) => (
                                <div
                                    key={i}
                                    className={`signal-dot ${i < getSignalLevel(meshScore) ? 'active' : ''}`}
                                />
                            ))}
                        </div>
                        <span className="signal-label">Score: {meshScore.toFixed(1)}</span>
                    </div>
                </div>
                <div className="header-actions">
                    <button onClick={handleAddContactById} className="add-contact-btn">
                        Add by ID
                    </button>
                    <button onClick={handleSendInvite} className="invite-btn">
                        Share Invite
                    </button>
                    <button onClick={() => setView('profile')} className="profile-btn">
                        Profile
                    </button>
                </div>
            </div>

            {notification && (
                <div className="notification-toast">
                    {notification}
                </div>
            )}

            <div className="contacts-container">
                <h2>Contacts</h2>

                {contacts.length === 0 ? (
                    <div className="empty-state">
                        <p>No contacts yet</p>
                        <p className="help-text">Share your invite link to connect with others!</p>
                    </div>
                ) : (
                    <div className="contacts-list">
                        {contacts.map((contact) => (
                            <div
                                key={contact.meshId}
                                className="contact-item"
                                onClick={() => handleSelectContact(contact)}
                            >
                                <div className="contact-avatar">
                                    {contact.nickname.charAt(0).toUpperCase()}
                                </div>
                                <div className="contact-info">
                                    <div className="contact-name">{contact.nickname}</div>
                                    <div className="contact-last-message">
                                        {contact.lastMessage || 'No messages yet'}
                                    </div>
                                </div>
                                {contact.unreadCount > 0 && (
                                    <div className="unread-badge">{contact.unreadCount}</div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
