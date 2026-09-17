package com.example.classlist.data

import android.content.Context
import androidx.core.content.edit
import java.net.URI
import java.util.Locale

object PortalSettings {
    const val HOME_URL = "https://yjs.cumtb.edu.cn/"
    private const val WEBSITE_HOST = "yjs.cumtb.edu.cn"
    private const val SYSTEM_HOST = "yjsglxt.cumtb.edu.cn"
    private const val SSO_HOST = "sso.cumtb.edu.cn"
    private const val LEGACY_PREFERENCES = "portal_settings"
    private const val LEGACY_URL_KEY = "portal_url"
    private val allowedHosts = setOf(WEBSITE_HOST, SYSTEM_HOST, SSO_HOST)

    fun isAllowed(url: String): Boolean = parse(url)?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) && uri.host?.lowercase(Locale.ROOT) in allowedHosts
    } ?: false

    fun isWebsiteHome(url: String): Boolean = parse(url)?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(WEBSITE_HOST, ignoreCase = true) &&
            uri.path.orEmpty() in setOf("", "/")
    } ?: false

    fun isOfficialEntry(url: String): Boolean = parse(url)?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(SYSTEM_HOST, ignoreCase = true) &&
            uri.path.equals("/ULogin.aspx", ignoreCase = true) &&
            uri.hasNonBlankQueryParameter("ticket")
    } ?: false

    fun canImport(url: String): Boolean = parse(url)?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(SYSTEM_HOST, ignoreCase = true) &&
            uri.path.orEmpty().startsWith("/Gstudent/", ignoreCase = true)
    } ?: false

    fun removeLegacySavedUrl(context: Context) {
        context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE).edit {
            remove(LEGACY_URL_KEY)
        }
    }

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()

    private fun URI.hasNonBlankQueryParameter(name: String): Boolean = rawQuery
        ?.split('&')
        ?.map { parameter -> parameter.substringBefore('=') to parameter.substringAfter('=', "") }
        ?.any { (key, value) -> key.equals(name, ignoreCase = true) && value.isNotBlank() }
        ?: false
}
