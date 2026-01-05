# Обновление ссылок на приглашение

## Выполненные изменения

### ✅ Web Client (HomeScreen.tsx)
**Файл:** `web-client/src/ui/HomeScreen.tsx`

**Было:**
```typescript
const baseUrl = `http://34.78.2.164:8001/invite/${myMeshId}`;
```

**Стало:**
```typescript
const baseUrl = `https://mesh-online.org/invite/${myMeshId}`;
```

**Эффект:** Теперь web клиент генерирует ссылки вида:
- `https://mesh-online.org/invite/{meshId}?nickname={nickname}`

---

### ✅ Android Client (ShareUtils.kt)
**Файл:** `app/src/main/java/com/mesh/client/utils/ShareUtils.kt`

**Было:**
```kotlin
val baseUrl = "http://34.78.2.164:8001"
```

**Стало:**
```kotlin
val baseUrl = "https://mesh-online.org"
```

**Эффект:** Android клиент генерирует ссылки вида:
- `https://mesh-online.org/invite/{meshId}?n={nickname}`

---

### ✅ AndroidManifest.xml (Deep Links)
**Файл:** `app/src/main/AndroidManifest.xml`

**Добавлена поддержка:**
```xml
<!-- Production domain with HTTPS -->
<data android:scheme="https" android:host="mesh-online.org" android:pathPrefix="/invite/" />
<!-- Fallback for HTTP (if needed) -->
<data android:scheme="http" android:host="mesh-online.org" android:pathPrefix="/invite/" />
<!-- Local development (emulator/localhost) -->
<data android:scheme="http" android:host="10.0.2.2" android:port="8080" android:pathPrefix="/invite/" />
```

**Удалено:**
```xml
<data android:scheme="http" android:host="34.78.2.164" android:port="8001" android:pathPrefix="/invite/" />
```

**Эффект:** 
- ✅ Android app теперь открывается по ссылкам `https://mesh-online.org/invite/{meshId}`
- ✅ Сохранена поддержка локальной разработки через localhost
- ✅ Включена Android App Links validation (`android:autoVerify="true"`)

---

## 📋 Требования к серверу

Для корректной работы deep links (Android App Links) необходимо:

### 1. Digital Asset Links файл
Создать файл на сервере: `https://mesh-online.org/.well-known/assetlinks.json`

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.mesh.client",
    "sha256_cert_fingerprints": [
      "YOUR_RELEASE_KEY_SHA256_FINGERPRINT_HERE"
    ]
  }
}]
```

**Как получить SHA256 fingerprint:**
```bash
# Для debug key:
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# Для release key:
keytool -list -v -keystore /path/to/release.keystore -alias your_alias
```

### 2. Invite handler на сервере
Убедиться, что сервер корректно обрабатывает:
- `https://mesh-online.org/invite/{meshId}`
- `https://mesh-online.org/invite/{meshId}?nickname={nickname}`

И возвращает HTML с:
- Meta tags для Open Graph
- JavaScript для автоматического открытия приложения
- Fallback кнопку "Open in App"

---

## ✅ Результат

**До изменений:**
- ❌ Ссылки: `http://34.78.2.164:8001/invite/{meshId}`
- ❌ Не работают deep links
- ❌ Видимый IP адрес

**После изменений:**
- ✅ Ссылки: `https://mesh-online.org/invite/{meshId}`
- ✅ HTTPS (безопасное соединение)
- ✅ Красивый домен
- ✅ Android App Links поддержка
- ✅ Единообразие между Web и Android

---

## 🔧 Тестирование

### Web Client
1. Открыть web клиент
2. Нажать "Share Invite"
3. Проверить, что ссылка начинается с `https://mesh-online.org/invite/`

### Android Client
1. Поделиться приглашением через Android app
2. Проверить, что ссылка начинается с `https://mesh-online.org/invite/`
3. Отправить ссылку себе и нажать на неё
4. Проверить, что открывается Android app (если установлен), а не браузер

---

## ⚠️ Важные замечания

1. **SSL сертификат**: Убедитесь, что `mesh-online.org` имеет валидный SSL сертификат (Let's Encrypt)
2. **DNS**: Убедитесь, что домен корректно резолвится
3. **Сервер**: Nginx/FastAPI должен обрабатывать `/invite/{meshId}` пути
4. **Asset Links**: Файл `.well-known/assetlinks.json` обязателен для Android App Links
