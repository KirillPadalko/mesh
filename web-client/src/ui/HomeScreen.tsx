import { useState, useEffect } from 'react';
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

export function HomeScreen() {
    const [meshId, setMeshId] = useState('');
    const [nickname, setNickname] = useState('');
    const [meshScore, setMeshScore] = useState(0);
    const [contacts, setContacts] = useState<Contact[]>([]);
    const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
    const [view, setView] = useState<'contacts' | 'chat' | 'profile' | 'network'>('contacts');
    const [transport, setTransport] = useState<ChatTransport | null>(null);
    const [inviteManager, setInviteManager] = useState<InviteManager | null>(null);
    const [notification, setNotification] = useState<string | null>(null);
    const [, setInviteLink] = useState('');

    useEffect(() => {
        initApp();

        // Subscribe to Mesh Graph events
        meshGraphManager.on('l2-update', (data: any) => {
            const viaContact = data.via?.substring(0, 8) || 'unknown';
            setNotification(`New L2 Connection discovered via ${data.via.substring(0, 4)}!`);

            // Show browser notification
            notificationManager.notifyL2Connection(viaContact);

            handleGraphChange();
            setTimeout(() => setNotification(null), 5000);
        });

        meshGraphManager.on('contact-update', () => {
            console.debug('[HomeScreen] contact-update event received');
            handleGraphChange();
        });
    }, []);

    const handleGraphChange = async () => {
        const newScore = meshGraphManager.getMeshScore();
        console.debug(`[HomeScreen] handleGraphChange called, updating meshScore to ${newScore}`);
        setMeshScore(newScore);
        await loadContacts();
    };

    const initApp = async () => {
        const id = await identityManager.getMeshId();
        const nick = identityManager.getLocalNickname();
        const score = meshGraphManager.getMeshScore();

        setMeshId(id || '');
        setNickname(nick);
        setMeshScore(score);

        // Request notification permission
        await notificationManager.requestPermission();

        if (id) {
            // Initialize network stack
            const cryptoManager = new CryptoManager(identityManager);
            const wsService = new WebSocketService();
            const webRTC = new WebRTCManager(wsService);
            const chatTransport = new ChatTransport(cryptoManager, webRTC, wsService);

            const inviteManager = new InviteManager(identityManager, cryptoManager, meshGraphManager);

            // Set up listeners
            chatTransport.listener = {
                onMessageReceived: async (fromMeshId, text, timestamp) => {
                    // Ensure contact exists
                    await ensureContact(fromMeshId);

                    // Get contact info for notification
                    const contact = await db.getContact(fromMeshId);
                    const nickname = contact?.nickname || `User ${fromMeshId.substring(0, 4)}`;

                    // Save message to DB
                    await db.insertMessage({
                        peerId: fromMeshId,
                        isIncoming: true,
                        text,
                        timestamp,
                        status: 'received',
                    });

                    // Show notification
                    await notificationManager.notifyNewMessage(nickname, text, fromMeshId);

                    // Refresh contacts
                    loadContacts();
                },

                onMessageStatusChanged: (peerId, isP2P) => {
                    console.log(`Connection status to ${peerId}: ${isP2P ? 'P2P' : 'Server'}`);
                },

                onInviteReceived: async (fromMeshId, inviteJson) => {
                    const invite = JSON.parse(inviteJson);
                    const ack = await inviteManager.processInvite(invite);

                    if (ack) {
                        const contactNickname = invite.nickname || `User ${fromMeshId.substring(0, 4)}`;

                        // Add to graph
                        meshGraphManager.addL1Connection(fromMeshId);

                        // Add to contacts
                        await db.insertContact({
                            meshId: fromMeshId,
                            nickname: contactNickname,
                        });

                        // Show notification for new contact
                        await notificationManager.notifyNewContact(contactNickname, fromMeshId);

                        // Send ACK
                        const ackJson = JSON.stringify(ack);
                        chatTransport.sendInviteAck(fromMeshId, ackJson);

                        // Refresh
                        loadContacts();
                        setMeshScore(meshGraphManager.getMeshScore());
                    }
                },

                onInviteAckReceived: async (fromMeshId, ackJson) => {
                    const ack = JSON.parse(ackJson);
                    const valid = await inviteManager.processInviteAck(ack);

                    if (valid) {
                        // Ensure contact exists
                        await ensureContact(fromMeshId, ack.nickname);
                        loadContacts();
                    }
                },

                onL2NotifyReceived: (fromMeshId, _notifyJson) => {
                    console.log('L2 notify received:', fromMeshId);
                    // Handle L2 notifications from network (if distinct from local graph updates)
                },

                onTransportError: (message) => {
                    console.error('Transport error:', message);
                },
            };

            wsService.connect(id);
            setTransport(chatTransport);
            setInviteManager(inviteManager);

            // 1. Sync contacts from Graph to DB (in case of missed inserts)
            const l1Connections = meshGraphManager.getL1Connections();
            for (const peerId of l1Connections) {
                await ensureContact(peerId);
            }

            // 2. Handle pending invite after stack is ready
            const pendingInvite = localStorage.getItem('meshPendingInvite');
            const pendingInviteNickname = localStorage.getItem('meshPendingInviteNickname');

            if (pendingInvite && pendingInvite !== id) {
                console.log('Found pending invite to', pendingInvite);
                localStorage.removeItem('meshPendingInvite');
                localStorage.removeItem('meshPendingInviteNickname');

                // Add to graph
                meshGraphManager.addL1Connection(pendingInvite);

                // Add to DB with nickname if available
                await ensureContact(pendingInvite, pendingInviteNickname || undefined);

                // Send actual invite message so peer adds us back
                const inviteJson = JSON.stringify(await inviteManager.createInvite(pendingInvite));
                chatTransport.sendInvite(pendingInvite, inviteJson);
            }

            // Initial contacts load
            await loadContacts();
        }
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


    const handleSelectContact = (contact: Contact) => {
        setSelectedContact(contact);
        setView('chat');
    };

    const loadContacts = async () => {
        const loaded = await db.getContactsWithPreview();
        setContacts(loaded);
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
                        <img src={`${import.meta.env.BASE_URL}logo.png`} alt="Mesh" className="app-logo" />
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
