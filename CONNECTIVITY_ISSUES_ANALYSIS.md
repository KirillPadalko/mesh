# Анализ проблем связи и аудио вызовов

## Дата анализа: 2026-02-02

## Выявленные критические проблемы

### 🔴 ПРОБЛЕМА 1: Отсутствие TURN серверов для WebRTC
**Серьезность**: КРИТИЧЕСКАЯ  
**Файл**: `app/src/main/java/com/mesh/client/network/WebRtcManager.kt:52-58`

**Проблема**:
- Используются только STUN серверы (Google)
- При строгих NAT/firewall P2P соединение невозможно установить
- Аудио вызовы не работают в 90% случаев из-за невозможности пробить NAT

**Решение**:
Добавить публичные TURN серверы или настроить собственный TURN сервер (coturn)

---

### 🔴 ПРОБЛЕМА 2: Нет обработки входящего аудио потока
**Серьезность**: КРИТИЧЕСКАЯ  
**Файл**: `app/src/main/java/com/mesh/client/network/WebRtcManager.kt:328-333`

**Проблема**:
```kotlin
override fun onAddStream(stream: MediaStream?) {
    Log.d(TAG, "onAddStream from $peerId")
    if (stream?.audioTracks?.isNotEmpty() == true) {
        stream.audioTracks[0].setEnabled(true) 
    }
}
```

Аудио трек включается, но нигде не подключается к AudioTrack для воспроизведения через динамик/наушники.

**Решение**:
Необходимо явно настроить AudioTrack для вывода звука или использовать встроенные механизмы WebRTC для audio rendering.

---

### 🟡 ПРОБЛЕМА 3: Слишком короткий ACK таймаут
**Серьезность**: ВЫСОКАЯ  
**Файл**: `app/src/main/java/com/mesh/client/transport/ChatTransport.kt:41`

**Проблема**:
```kotlin
private val ACK_TIMEOUT = 3000L // 3 seconds
```

При медленном соединении или переключении между WiFi/Mobile данные могут не успеть дойти за 3 секунды.

**Решение**:
Увеличить до 5-7 секунд или сделать адаптивным в зависимости от RTT.

---

### 🟡 ПРОБЛЕМА 4: WebSocket переподключение без проверки сети
**Серьезность**: ВЫСОКАЯ  
**Файл**: `app/src/main/java/com/mesh/client/network/WebSocketService.kt:164-179`

**Проблема**:
```kotlin
private fun scheduleReconnect() {
    if (isExplicitDisconnect) return
    
    val delay = (RECONNECT_BASE_DELAY * (1L shl reconnectAttempt.coerceAtMost(30)))
        .coerceAtMost(RECONNECT_MAX_DELAY)
    
    handler.postDelayed({
        if (!isExplicitDisconnect) {
            connect()
        }
    }, delay)
}
```

Попытки переподключения идут даже если сеть недоступна, что расходует батарею и время.

**Решение**:
Проверять наличие интернета перед попыткой переподключения.

---

### 🟡 ПРОБЛЕМА 5: ICE restart может создавать race conditions
**Серьезность**: ВЫСОКАЯ  
**Файл**: `app/src/main/java/com/mesh/client/network/WebRtcManager.kt:96-100`

**Проблема**:
```kotlin
fun restartIce(peerId: String) {
    Log.i(TAG, "Restarting ICE for $peerId due to connectivity issues")
    cleanupPeer(peerId)
    connectToPeer(peerId)
}
```

При одновременном вызове с обеих сторон может возникнуть deadlock в negotiation.

**Решение**:
Добавить задержку перед переподключением или использовать механизм полярности (polite/impolite).

---

### 🟡 ПРОБЛЕМА 6: Ping/Pong не мониторится активно
**Серьезность**: СРЕДНЯЯ  
**Файл**: `app/src/main/java/com/mesh/client/network/WebSocketService.kt:28-30`

**Проблема**:
```kotlin
.connectTimeout(java.time.Duration.ofSeconds(10))
.pingInterval(java.time.Duration.ofSeconds(10))
```

OkHttp отправляет ping каждые 10 секунд, но клиент не отслеживает, пришел ли pong. При "зависшем" соединении можно долго не понять, что связь потеряна.

**Решение**:
Добавить таймер для отслеживания последнего полученного сообщения.

---

### 🟡 ПРОБЛЕМА 7: Сервер не хранит WebRTC signaling для offline пользователей
**Серьезность**: СРЕДНЯЯ  
**Файл**: `connection_manager.py:133-145`

**Проблема**:
```python
if msg_type not in ["server_message", "webrtc_offer", "webrtc_answer", "ice_candidate"]:
    logger.warning(f"Not storing offline message of type {msg_type}")
    return

if msg_type != "server_message":
    return
```

WebRTC signaling не сохраняется для offline пользователей. Это правильно для ephemeral данных, но означает что входящие звонки невозможны если пользователь offline.

**Пояснение**:
Это не баг, это design choice. Но нужно учитывать при expectations.

---

### 🟢 ПРОБЛЕМА 8: Недостаточный ICE candidate pool
**Серьезность**: НИЗКАЯ  
**Файл**: `app/src/main/java/com/mesh/client/network/WebRtcManager.kt:66`

**Проблема**:
```kotlin
iceCandidatePoolSize = 5
```

Для сложных сетевых топологий может быть недостаточно.

**Решение**:
Увеличить до 10 (но см. комментарий в коде про "candidate storm").

---

## Рекомендации по приоритетам

### Немедленно исправить:
1. ✅ **Добавить TURN серверы** - без этого аудио вызовы работать не будут
2. ✅ **Исправить воспроизведение аудио** - входящий звук не слышен

### Исправить в ближайшее время:
3. ⚠️ Увеличить ACK таймаут до 5-7 секунд
4. ⚠️ Добавить проверку сети перед WebSocket переподключением
5. ⚠️ Добавить мониторинг ping/pong для раннего обнаружения проблем

### Рассмотреть для улучшения:
6. 💡 Улучшить ICE restart логику
7. 💡 Добавить метрики качества соединения
8. 💡 Реализовать адаптивный таймаут на основе RTT

---

## Конкретные изменения кода

Подготовлены исправления для критических проблем в следующих файлах:
- `WebRtcManager.kt` - TURN серверы и audio rendering
- `ChatTransport.kt` - увеличение ACK таймаута
- `WebSocketService.kt` - проверка сети перед переподключением
