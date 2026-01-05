package com.mesh.client.data

/**
 * MVP Local Storage (In-Memory).
 * Replace with Room Database for production.
 */
object LocalStorage {

    data class StoredMessage(
        val peerId: String,
        val isIncoming: Boolean,
        val text: String,
        val timestamp: Long
    )
    
    data class Contact(
        val meshId: String,
        val nickname: String,
        val lastMessage: String? = null,
        val lastMessageTime: Long? = null,
        val unreadCount: Int = 0
    )

    private val messages = mutableListOf<StoredMessage>()
    private val contacts = mutableMapOf<String, Contact>()
    private var context: android.content.Context? = null
    private val gson = com.google.gson.Gson()

    fun init(ctx: android.content.Context) {
        context = ctx.applicationContext
        loadContacts()
    }

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
        persistContacts()
    }

    fun getContact(meshId: String): Contact? {
        return contacts[meshId]
    }
    
    fun getContacts(): List<Contact> {
        return contacts.values.toList()
    }

    private fun persistContacts() {
        val ctx = context ?: return
        try {
            val json = gson.toJson(contacts.values.toList())
            ctx.openFileOutput("contacts.json", android.content.Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalStorage", "Error saving contacts", e)
        }
    }

    private fun loadContacts() {
        val ctx = context ?: return
        try {
            if (!ctx.fileList().contains("contacts.json")) return
            
            ctx.openFileInput("contacts.json").bufferedReader().use {
                val json = it.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<Contact>>() {}.type
                val list: List<Contact> = gson.fromJson(json, type)
                list.forEach { contact ->
                    contacts[contact.meshId] = contact
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalStorage", "Error loading contacts", e)
        }
    }
}
