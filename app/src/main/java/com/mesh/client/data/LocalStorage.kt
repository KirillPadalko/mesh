package com.mesh.client.data

/**
 * MVP Local Storage (In-Memory).
 * Replace with Room Database for production.
 */
object LocalStorage {

    data class StoredMessage(
        val peerId: String,
        val isIncoming: Boolean,
        val text: String, // Storing plaintext for UI (in real app, store ciphertext and decrypt on view)
        val timestamp: Long
    )
    
    data class Contact(
        val meshId: String,
        val nickname: String
    )

    private val messages = mutableListOf<StoredMessage>()
    private val contacts = mutableMapOf<String, Contact>()

    fun saveMessage(peerId: String, isIncoming: Boolean, text: String, timestamp: Long) {
        synchronized(messages) {
            messages.add(StoredMessage(peerId, isIncoming, text, timestamp))
        }
    }

    fun getMessages(peerId: String): List<StoredMessage> {
        synchronized(messages) {
            return messages.filter { it.peerId == peerId }.sortedBy { it.timestamp }
        }
    }

    fun saveContact(meshId: String, nickname: String) {
        contacts[meshId] = Contact(meshId, nickname)
    }

    fun getContact(meshId: String): Contact? {
        return contacts[meshId]
    }
}
