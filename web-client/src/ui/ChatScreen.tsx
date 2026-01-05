import React, { useState, useEffect, useRef } from 'react';
import { ChatTransport } from '../core/network/ChatTransport';
import { db } from '../core/storage/StorageManager';
import { Contact, Message } from '../types';
import './ChatScreen.css';

interface ChatScreenProps {
    contact: Contact;
    transport: ChatTransport | null;
    onBack: () => void;
}

export function ChatScreen({ contact, transport, onBack }: ChatScreenProps) {
    const [messages, setMessages] = useState<Message[]>([]);
    const [inputText, setInputText] = useState('');
    const [currentNickname, setCurrentNickname] = useState(contact.nickname);
    const messagesEndRef = useRef<HTMLDivElement>(null);

    const handleRenameContact = async () => {
        const newName = window.prompt(`Rename ${currentNickname}:`, currentNickname);
        if (newName && newName.trim() && newName !== currentNickname) {
            const trimmed = newName.trim();
            await db.insertContact({
                meshId: contact.meshId,
                nickname: trimmed
            });
            setCurrentNickname(trimmed);
        }
    };

    useEffect(() => {
        loadMessages();

        const interval = setInterval(loadMessages, 1000);
        return () => clearInterval(interval);
    }, [contact.meshId]);

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const loadMessages = async () => {
        const msgs = await db.getMessagesForPeer(contact.meshId);
        setMessages(msgs);

        // Mark incoming messages as read
        if (msgs.some(m => m.isIncoming && m.status !== 'read')) {
            await markAsRead();
        }
    };

    const markAsRead = async () => {
        await db.markMessagesAsRead(contact.meshId);
    };

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    const handleSend = async () => {
        if (!inputText.trim() || !transport) return;

        const text = inputText.trim();
        setInputText('');

        // Save outgoing message
        await db.insertMessage({
            peerId: contact.meshId,
            isIncoming: false,
            text,
            timestamp: Date.now(),
            status: 'sent',
        });

        // Send via transport
        await transport.sendMessage(contact.meshId, text);

        // Reload messages
        loadMessages();
    };

    const handleKeyPress = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <div className="chat-container">
            <div className="chat-header">
                <button onClick={onBack} className="back-btn">Back</button>
                <div className="chat-contact-info" onClick={handleRenameContact} style={{ cursor: 'pointer' }}>
                    <div className="chat-avatar">
                        {currentNickname.charAt(0).toUpperCase()}
                    </div>
                    <div>
                        <div className="chat-contact-name">{currentNickname}</div>
                        <div className="chat-contact-id">{contact.meshId.substring(0, 8)}...</div>
                    </div>
                </div>
            </div>

            <div className="messages-container">
                {messages.length === 0 ? (
                    <div className="empty-chat">
                        <p>No messages yet</p>
                        <p className="help-text">Start the conversation!</p>
                    </div>
                ) : (
                    messages.map((msg, index) => (
                        <div
                            key={msg.id || index}
                            className={`message ${msg.isIncoming ? 'incoming' : 'outgoing'}`}
                        >
                            <div className="message-bubble">
                                <div className="message-text">{msg.text}</div>
                                <div className="message-time">
                                    {new Date(msg.timestamp).toLocaleTimeString([], {
                                        hour: '2-digit',
                                        minute: '2-digit',
                                    })}
                                </div>
                            </div>
                        </div>
                    ))
                )}
                <div ref={messagesEndRef} />
            </div>

            <div className="chat-input-container">
                <input
                    type="text"
                    value={inputText}
                    onChange={(e) => setInputText(e.target.value)}
                    onKeyPress={handleKeyPress}
                    placeholder="Type a message..."
                    className="chat-input"
                />
                <button onClick={handleSend} disabled={!inputText.trim()} className="send-btn">
                    Send
                </button>
            </div>
        </div>
    );
}
