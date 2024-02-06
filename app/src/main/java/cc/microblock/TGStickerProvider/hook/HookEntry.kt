package cc.microblock.TGStickerProvider.hook

import android.annotation.SuppressLint
import android.database.CursorWindow
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import androidx.annotation.RequiresApi
import cc.microblock.TGStickerProvider.BuildConfig
import cc.microblock.TGStickerProvider.destDataPath
import cc.microblock.TGStickerProvider.hook.TelegramTLParser.SerializedData
import cc.microblock.TGStickerProvider.hook.TelegramTLParser.TLRPC
import cc.microblock.TGStickerProvider.nomediaPath
import cc.microblock.TGStickerProvider.nomediaPath2
import cc.microblock.TGStickerProvider.stickerDataPath
import cc.microblock.TGStickerProvider.syncFlagsPath
import cc.microblock.TGStickerProvider.tgspDataPath
import cc.microblock.TGStickerProvider.utils.CachePathHelper.getCachePath
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import java.io.File
import java.lang.reflect.Field
import kotlin.concurrent.thread


@InjectYukiHookWithXposed(entryClassName = "TGStickerProvider", isUsingXposedModuleStatus = true)
class HookEntry : IYukiHookXposedInit {

    @SuppressLint("DiscouragedPrivateApi")
    override fun onInit() = configs {
        debugLog {
            tag = "TGStickerProvider"
            isEnable = BuildConfig.DEBUG
        }

        // Fix cannot read database that is larger than 2MB
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 100 * 1024 * 1024) // 100MB is the new size
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SdCardPath")
    override fun onHook() = encase {
        if (this.packageName.startsWith("android")) return@encase

        val ignoreSet = HashSet<Int>()
        thread(true) {
            Thread.sleep(1000)
            val dataPath = "/data/data/${this.packageName}/"
            val tgCachePath = getCachePath(this.appContext, this.packageName)

            if (!File(tgspDataPath).exists()) {
                File(tgspDataPath).mkdirs()
                File(stickerDataPath).mkdirs()
                File(syncFlagsPath).mkdirs()
                File(destDataPath).mkdirs()
            }

            if (!File(nomediaPath).exists()) {
                File(nomediaPath).createNewFile()
            }

            while (true) {
                try {
                    fun checkDb(dbPath: String, sheetName: String): Boolean {
                        val cache4DB = File(dbPath)
                        val dedupSet = HashSet<Int>()
                        if (cache4DB.exists()) {
                            val cache4DBConn = SQLiteDatabase.openDatabase(
                                cache4DB.path,
                                null,
                                SQLiteDatabase.OPEN_READONLY
                            )

                            val cursor =
                                cache4DBConn.rawQuery("SELECT id,data,hash FROM $sheetName", null)

                            val stickerSets = ArrayList<TLRPC.TL_messages_stickerSet>()

                            while (cursor.moveToNext()) {
                                val data = cursor.getBlob(1)
                                val stream = SerializedData(data)

                                try {
                                    var constructorId = stream.readInt32(true)
                                    var count = 1

                                    // Compatibility with newer DB sheets(stickersets2, stickerset)
                                    if (constructorId != 0x6e153f16) {
                                        count = constructorId
                                        constructorId = stream.readInt32(true)
                                    }
                                    if (constructorId != 0x6e153f16) {
                                        YLog.error("constructorId != 0x6e153f16")
                                        continue
                                    }

                                    for (i in 0 until count) {
                                        try {
                                            if (i != 0) stream.readInt32(true)
                                            val stickerSet =
                                                TLRPC.TL_messages_stickerSet.TLdeserialize(
                                                    stream,
                                                    constructorId,
                                                    true
                                                )
                                            val hash = stickerSet.set.hash
                                            if (dedupSet.contains(hash)) continue
                                            dedupSet.add(hash)
                                            stickerSets.add(stickerSet)
                                        } catch (e: Exception) {
                                            YLog.warn("", e)
                                            continue
                                        }
                                    }


                                } catch (e: Exception) {
                                    YLog.warn("", e)
                                    continue
                                }

                            }

                            cursor.close()
                            cache4DBConn.close()

                            for (stickerSet in stickerSets) {
                                // use .txt.jpg to bypass the file type check in android 11+
                                val stickerNameFile =
                                    File(
                                        stickerDataPath,
                                        "${stickerSet.set.hash}.stickerData.txt.jpg"
                                    )
                                try {
                                    File(stickerDataPath).mkdirs()
                                    stickerNameFile.createNewFile()
                                    stickerNameFile.writeText(
                                        stickerSet.set.short_name + "\n" +
                                                stickerSet.set.title + "\n" +
                                                stickerSet.set.count + "\n" +
                                                stickerSet.documents[0]?.mime_type
                                    )
                                } catch (e: Exception) {
                                    YLog.error(e.toString())
                                    // has occupied by another app
                                    continue
                                }

                                var fullSync = true
                                val destDir =
                                    "${destDataPath}/tgSync_${stickerSet.set.short_name}"
                                File(destDir).mkdirs()

                                var lowQualityCount = 0
                                var highQualityCount = 0
                                var printLog =
                                    false // only print log if there is a new sticker

                                for (sticker in stickerSet.documents) {
                                    when(sticker.mime_type) {
                                        "image/webp" -> {
                                            val localPath =
                                                "${sticker.dc_id}_${sticker.id}.webp"
                                            val localPathLowQuality =
                                                "-${sticker.id}_1109.webp"
                                            val stickerFile = File(tgCachePath, localPath)
                                            val destFile =
                                                File(destDir, "${sticker.id}_high.webp")
                                            val destFileLowQuality =
                                                File(destDir, "${sticker.id}_low.webp")

                                            if (stickerFile.exists()) {
                                                highQualityCount++
                                                if (!destFile.exists()) {
                                                    printLog = true
                                                    stickerFile.copyTo(destFile)
                                                    if (destFileLowQuality.exists()) {
                                                        destFileLowQuality.delete()
                                                    }
                                                }
                                            } else if (File(
                                                    tgCachePath,
                                                    localPathLowQuality
                                                ).exists()
                                            ) {
                                                lowQualityCount++
                                                if (!destFileLowQuality.exists() && !destFile.exists()) {
                                                    printLog = true
                                                    File(
                                                        tgCachePath,
                                                        localPathLowQuality
                                                    ).copyTo(destFileLowQuality)
                                                }
                                            } else {
                                                if (!destFile.exists()) fullSync = false
                                            }
                                        }
                                        "video/webm" -> {
                                            val localPath =
                                                "${sticker.dc_id}_${sticker.id}.webm"
                                            val stickerFile = File(tgCachePath, localPath)
                                            val destFile =
                                                File(destDir, "${sticker.id}_high.webm")
                                            if (stickerFile.exists()) {
                                                highQualityCount++
                                                if (!destFile.exists()) {
                                                    printLog = true
                                                    stickerFile.copyTo(destFile)
                                                }
                                            }
                                        }
                                        else -> {
//                                            YLog.debug("Unknown mime_type: ${sticker.mime_type}")
                                            continue;
                                        }
                                    }
                                }

                                if (printLog)
                                    YLog.debug("*new* [${lowQualityCount + highQualityCount}(Low$lowQualityCount High${highQualityCount})/${stickerSet.set.count}] ${stickerSet.set.title} ${stickerSet.set.short_name} ${stickerSet.set.count} ${stickerSet.set.hash}")
                                if (fullSync) ignoreSet.add(stickerSet.set.hash)
                            }
                        }

                        return true
                    }

                    // Main DB
                    checkDb("${dataPath}/files/cache4.db", "stickers_v2")

                    // Separate DBs for each account
                    File("$dataPath/files/").listFiles()?.filter {
                        it.isDirectory && it.name.startsWith("account")
                    }?.forEach {
                        checkDb("${it.path}/cache4.db", "stickers_v2")
                    }
                } catch (e: Exception) {
                    YLog.debug(e.toString())
                }

                Thread.sleep(40 * 1000)
            }
        }
    }
}