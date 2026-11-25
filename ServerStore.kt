package com.mysql.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class ServerStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mysql_app_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock", false)
        set(v) = prefs.edit().putBoolean("lock", v).apply()

    fun load(): List<Server> {
        val raw = prefs.getString("servers", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Server(
                o.getString("id"), o.getString("name"), o.getString("url"),
                o.getString("user"), o.getString("pass"), o.getBoolean("auto")
            )
        }
    }

    fun save(list: List<Server>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("url", it.url)
                put("user", it.user); put("pass", it.pass); put("auto", it.autoLogin)
            })
        }
        prefs.edit().putString("servers", arr.toString()).apply()
    }
}
