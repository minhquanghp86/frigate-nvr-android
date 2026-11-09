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

    fun addAcceptedCertFingerprint(fingerprint: String, organization: String? = null) {
        if (fingerprint.isBlank()) return
        val fp = fingerprint.lowercase()
        val updated = getAcceptedCertFingerprints().toMutableSet().apply { add(fp) }
        val meta = getAcceptedCertOrgs().toMutableMap()
        if (!organization.isNullOrBlank()) meta[fp] = organization else meta.remove(fp)
        prefs.edit()
            .putStringSet(KEY_CERT_FINGERPRINTS, updated)
            .putStringSet(KEY_CERT_ORGS, meta.map { (k, v) -> "$k|$v" }.toSet())
            .apply()
    }

    /** Thay toàn bộ pin bằng đúng cert mới (dùng khi Frigate xoay cert mặc định). */
    fun replaceAcceptedCert(fingerprint: String, organization: String?) {
        if (fingerprint.isBlank()) return
        val fp = fingerprint.lowercase()
        val metaSet = if (organization.isNullOrBlank()) emptySet() else setOf("$fp|$organization")
        prefs.edit()
            .putStringSet(KEY_CERT_FINGERPRINTS, setOf(fp))
            .putStringSet(KEY_CERT_ORGS, metaSet)
            .apply()
    }

    /** fingerprint -> organization (O trong subject) của các cert đã pin. */
    fun getAcceptedCertOrgs(): Map<String, String> =
        prefs.getStringSet(KEY_CERT_ORGS, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val idx = entry.indexOf('|')
                if (idx <= 0) return@mapNotNull null
                entry.substring(0, idx) to entry.substring(idx + 1)
            }
            .toMap()

    fun clearAcceptedCertFingerprints() {
        prefs.edit().remove(KEY_CERT_FINGERPRINTS).remove(KEY_CERT_ORGS).apply()
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
        private const val KEY_CERT_ORGS = "accepted_cert_orgs"
    }
}
