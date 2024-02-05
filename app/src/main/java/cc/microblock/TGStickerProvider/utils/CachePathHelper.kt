package cc.microblock.TGStickerProvider.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import java.io.File


object CachePathHelper {
    @JvmStatic
    fun getCachePath(context: Application?, packageName: String): String {
        var defaultCachePath = "/storage/self/primary/Android/data/${packageName}/cache"
        if (context != null && isNekoX(packageName)) {
            val nekoXCachePath = getNekoXCachePath(context)
            if (nekoXCachePath != null) {
                defaultCachePath = "$nekoXCachePath/caches"
            }
        }
        return defaultCachePath
    }

    @JvmStatic
    fun isNekoX(packageName: String): Boolean {
        return File("/data/data/${packageName}/shared_prefs/nkmrcfg.xml").exists()
    }

    @JvmStatic
    fun getNekoXCachePath(context: Context): String? {
        val preferences: SharedPreferences =
            context.getSharedPreferences(
                "nkmrcfg",
                Context.MODE_PRIVATE
            )
        return preferences.getString("cache_path", null)
    }
}
