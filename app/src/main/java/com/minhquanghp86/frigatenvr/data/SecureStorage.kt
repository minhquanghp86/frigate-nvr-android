package com.minhquanghp86.frigatenvr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Lưu trữ bí mật đã mã hóa (mật khẩu Frigate, JWT, token Home Assistant).
 *
 * Trước đây các giá trị này nằm plaintext trong DataStore Preferences —
 * bất kỳ app nào có quyền đọc file của app (máy đã root, backup, ...) đều
 * đọc được. Từ nay chúng chỉ nằm trong EncryptedSharedPreferences, khóa
 * được giữ trong Android Keystore (AES256-GCM), không bao giờ rời thiết bị.
 *
 * Các dữ liệu không nhạy cảm (URL, username, tùy chọn UI) vẫn ở DataStore.
 */
class SecureStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "").orEmpty()
    fun putPassword(value: String) {
        if (value.isBlank()) prefs.edit().remove(KEY_PASSWORD).apply()
        else prefs.edit().putString(KEY_PASSWORD, value).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun putToken(value: String?) {
        if (value.isNullOrBlank()) prefs.edit().remove(KEY_TOKEN).apply()
        else prefs.edit().putString(KEY_TOKEN, value).apply()
    }

    fun getHaToken(): String = prefs.getString(KEY_HA_TOKEN, "").orEmpty()
    fun putHaToken(value: String) {
        if (value.isBlank()) prefs.edit().remove(KEY_HA_TOKEN).apply()
        else prefs.edit().putString(KEY_HA_TOKEN, value).apply()
    }

    /** SHA-256 fingerprint (hex) của các cert tự ký mà người dùng đã chấp nhận. */
    fun getAcceptedCertFingerprints(): Set<String> =
        prefs.getStringSet(KEY_CERT_FINGERPRINTS, emptySet()).orEmpty()

    fun addAcceptedCertFingerprint(fingerprint: String) {
        if (fingerprint.isBlank()) return
        val updated = getAcceptedCertFingerprints().toMutableSet().apply { add(fingerprint.lowercase()) }
        prefs.edit().putStringSet(KEY_CERT_FINGERPRINTS, updated).apply()
    }

    fun clearAcceptedCertFingerprints() {
        prefs.edit().remove(KEY_CERT_FINGERPRINTS).apply()
    }

    fun clearSecrets() {
        prefs.edit()
            .remove(KEY_PASSWORD)
            .remove(KEY_TOKEN)
            .remove(KEY_HA_TOKEN)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "frigate_secrets"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOKEN = "token"
        private const val KEY_HA_TOKEN = "ha_token"
        private const val KEY_CERT_FINGERPRINTS = "accepted_cert_fingerprints"
    }
}
