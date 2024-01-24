@file:Suppress("SetTextI18n", "Deprecation")

package cc.microblock.TGStickerProvider.ui.activity

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import cc.microblock.TGStickerProvider.BuildConfig
import cc.microblock.TGStickerProvider.R
import cc.microblock.TGStickerProvider.dPath
import cc.microblock.TGStickerProvider.databinding.ActivityMainBinding
import cc.microblock.TGStickerProvider.destDataPath
import cc.microblock.TGStickerProvider.realDataPath
import cc.microblock.TGStickerProvider.stickerDataPath
import cc.microblock.TGStickerProvider.tgseDataPath
import cc.microblock.TGStickerProvider.ui.activity.base.BaseActivity
import com.google.android.material.textview.MaterialTextView
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.File
import kotlin.concurrent.thread


data class StickerState(var highQuality: Int, var lowQuality: Int, var all: Int)
data class StickerInfo(
    val name: String,
    val id: String,
    val hash: String,
    val remoteState: StickerState,
    val syncedState: StickerState,
    val all: Int
)

class RecyclerAdapterStickerList(val act: MainActivity) :
    RecyclerView.Adapter<RecyclerAdapterStickerList.ViewHolder>() {
    var stickerList = mutableListOf<StickerInfo>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name = view.findViewById<android.widget.TextView>(R.id.sticker_name)
        val id = view.findViewById<android.widget.TextView>(R.id.sticker_id)
        val syncState = view.findViewById<android.widget.TextView>(R.id.syncState)
        val syncBtn = view.findViewById<android.widget.Button>(R.id.syncBtn)
        val rmBtn = view.findViewById<MaterialTextView>(R.id.rmBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false)
        return ViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = stickerList[position]
        holder.name.text = s.name
        holder.id.text = s.id
        holder.syncState.text =
            "同步 ${s.syncedState.all}-l${s.syncedState.lowQuality}-h${s.syncedState.highQuality} / 缓存 ${stickerList[position].remoteState.all}-l${s.remoteState.lowQuality}-h${s.remoteState.highQuality} / 总 ${s.all}"
        holder.syncBtn.setOnClickListener {
            val pd = ProgressDialog(act)
            pd.setMessage("正在同步")
            pd.show()
            pd.setCancelable(false)
            thread(true) {
                val remoteFolder = "${destDataPath}tgSync_${s.id}"
                val syncedFolder = "${realDataPath}tgSync_${s.id}"
                if (!File(syncedFolder).exists()) {
                    File(syncedFolder).mkdirs()
                }

                val remoteFileList = File(remoteFolder).listFiles()
                val syncedFileList = File(syncedFolder).listFiles()
                fun findExistingFilesById(id: String): ArrayList<File> {
                    val result = ArrayList<File>()
                    if (syncedFileList != null) {
                        for (file in syncedFileList) {
                            if (file.name.startsWith(id)) {
                                result.add(file)
                            }
                        }
                    }
                    return result
                }

                var index = 0
                if (remoteFileList != null) {
                    for (remoteFile in remoteFileList) {
                        val nameWithoutExt =
                            remoteFile.name.substring(0, remoteFile.name.indexOf("."))
                        val id = nameWithoutExt.substring(0, nameWithoutExt.indexOf("_"))
                        val outPathPng = "${syncedFolder}/${nameWithoutExt}.png"
                        val outPathGif = "${syncedFolder}/${nameWithoutExt}.gif"
                        val outPathWebp = "${syncedFolder}/${nameWithoutExt}.webp"

                        val existing = findExistingFilesById(id)
                        if (existing.size == 0 || existing[0].name.endsWith(".webp")) {
                            if (remoteFile.name.endsWith(".webp")) {
                                val decoder = ImageDecoder.createSource(remoteFile)
                                val bitmap = ImageDecoder.decodeBitmap(decoder)
                                val out = File(outPathPng)
                                if (!out.exists()) {
                                    out.createNewFile()
                                }
                                bitmap.compress(
                                    android.graphics.Bitmap.CompressFormat.PNG,
                                    100,
                                    out.outputStream()
                                )

                                val fWebp = File(outPathWebp)
                                if (fWebp.exists()) {
                                    fWebp.delete()
                                }

                                for (file in existing) {
                                    file.delete()
                                }
                            } else {
                                remoteFile.copyTo(File("${syncedFolder}/${remoteFile.name}"), true)
                            }
                        }

                        index++
                        act.runOnUiThread {
                            pd.setMessage("正在同步 ${index}/${remoteFileList.size}")
                        }
                    }
                }
                act.runOnUiThread {
                    pd.setMessage("正在更新列表")
                }
                act.updateStickerList()
                act.runOnUiThread {
                    pd.dismiss()
                }
            }
        }

        holder.rmBtn.setOnClickListener {
            // dialog confirm
            AlertDialog.Builder(act)
                .setTitle("删除已同步的表情包集 ${s.name}")
                .setMessage("你将删除已同步的表情包集 ${s.name}，这将会删除 ${s.syncedState.all} 个表情包文件，是否继续？")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("删除") { dialog, whichButton ->
                    val pd = ProgressDialog(act)
                    pd.setMessage("正在删除")
                    pd.show()
                    pd.setCancelable(false)
                    Thread {
                        //                            val remoteFolder = "${destDataPath}tgSync_${s.id}"
                        val syncedFolder = "${realDataPath}tgSync_${s.id}"
                        //                            File(remoteFolder).deleteRecursively()
                        File(syncedFolder).deleteRecursively()
                        act.runOnUiThread {
                            pd.setMessage("正在更新列表")
                        }
                        act.updateStickerList()
                        act.runOnUiThread {
                            pd.dismiss()
                        }
                    }.start()
                }
                .setNegativeButton("算了", null).show()
        }
        //        holder.hash.text = stickerList[position].hash
    }

    override fun getItemCount(): Int {
        return stickerList.size
    }

}

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private fun requestmanageexternalstorage_Permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 先判断有没有权限
            if (Environment.isExternalStorageManager()) {
                //                Toast.makeText(
                //                    this,
                //                    "Android VERSION  R OR ABOVE，HAVE MANAGE_EXTERNAL_STORAGE GRANTED!",
                //                    Toast.LENGTH_LONG
                //                ).show()
                onGainedPermission()
            } else {
                Toast.makeText(
                    this,
                    "请授予插件完全文件访问权限，以同步表情包~",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.setData(Uri.parse("package:" + this.packageName))
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 2339)
            }
        }
    }

    fun onGainedPermission() {
        Toast.makeText(
            this,
            "权限获取成功，可以同步表情包啦~",
            Toast.LENGTH_LONG
        ).show()

        updateStickerList()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2339) {
            if (Environment.isExternalStorageManager()) {
                onGainedPermission()
            } else {
                Toast.makeText(
                    this,
                    "请授予插件完全文件访问权限，以同步表情包~",
                    Toast.LENGTH_LONG
                ).show()
                requestmanageexternalstorage_Permission()
            }
        }

    }

    override fun onCreate() {
        refreshModuleStatus()
        requestmanageexternalstorage_Permission()
        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)
        binding.button2.setOnClickListener {
            binding.tips.visibility = View.GONE
            binding.manage.visibility = View.VISIBLE
        }

        binding.resetBtn.setOnClickListener {
            val pd = ProgressDialog(this)
            pd.setMessage("正在清除")
            pd.show()
            pd.setCancelable(false)
            Thread {
                File(dPath).deleteRecursively()
                updateStickerList()
                pd.dismiss()
            }.start()
        }

        binding.stickerManageView.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.stickerManageView.adapter = RecyclerAdapterStickerList(this)

        stickerList.observe(this) {
            runOnUiThread {
                (binding.stickerManageView.adapter as RecyclerAdapterStickerList).stickerList = it
                binding.stickerManageView.adapter?.notifyDataSetChanged()
            }
        }

        thread(true) {
            while (true) {
                Thread.sleep(2000)
                updateStickerList()
            }
        }
    }

    val stickerList: MutableLiveData<MutableList<StickerInfo>> by lazy {
        MutableLiveData<MutableList<StickerInfo>>(mutableListOf())
    }

    fun updateStickerList() {
        YLog.info("start")
        //        YLog.debug("Root read test: ${File("/storage/emulated/0").listFiles().size}")
        if (!File(tgseDataPath).exists()) {
            this.stickerList.postValue(mutableListOf())
            return
        }

        val ignoreIds = File("$dPath/ignore.txt").run {
            if (exists()) readLines().filterNot { it.startsWith("#") }
            else {
                writeText(
                    """# write the ignored sticker IDs here
                    |# separate with line breaks
                """.trimMargin()
                )
                emptyList()
            }
        }
        YLog.info("ignoreIds: [${ignoreIds.joinToString { "`$it`" }}]")

        val stickerList = mutableListOf<StickerInfo>()
        for (file in File(stickerDataPath).listFiles() ?: return) {
            try {
                val hash = file.name
                if (!file.name.endsWith(".stickerData.txt.jpg")) continue
                val lines = File(file.absolutePath).readLines()
                val id = lines[0]
                if (id in ignoreIds) continue
                val name = lines[1]
                val allSize = lines[2].toInt()

                if (stickerList.any { it.hash == hash }) continue

                val remoteState = StickerState(0, 0, 0)
                val syncedState = StickerState(0, 0, 0)

                val remoteFolder = "${destDataPath}tgSync_${id}"
                if (File(remoteFolder).exists()) {
                    val remoteFileList = File(remoteFolder).listFiles()
                    if (remoteFileList != null) {
                        for (remoteFile in remoteFileList) {
                            if (remoteFile.name.endsWith("_low.webp")) {
                                remoteState.lowQuality++
                            } else if (remoteFile.name.endsWith("_high.webp")) {
                                remoteState.highQuality++
                            }

                            remoteState.all++
                        }
                    }
                }

                val syncedFolder = "${realDataPath}tgSync_${id}"
                if (File(syncedFolder).exists()) {
                    val syncedFileList = File(syncedFolder).listFiles()
                    if (syncedFileList != null) {
                        for (syncedFile in syncedFileList) {
                            if (syncedFile.name.contains("_low.")) {
                                syncedState.lowQuality++
                            } else if (syncedFile.name.contains("_high.")) {
                                syncedState.highQuality++
                            }

                            syncedState.all++
                        }
                    }
                }

                stickerList.add(StickerInfo(name, id, hash, remoteState, syncedState, allSize))
            } catch (e: Exception) {
                YLog.error("Error while parsing ${file.name}", e)
            }
        }

        // resort by remoteState.all
        stickerList.sortByDescending { it.remoteState.all }

        this.stickerList.postValue(stickerList)
    }

    /**
     * Hide or show launcher icons
     * - You may need the latest version of LSPosed to enable the function of
     *   hiding launcher icons in higher version systems
     *
     * 隐藏或显示启动器图标
     * - 你可能需要 LSPosed 的最新版本以开启高版本系统中隐藏 APP 桌面图标功能
     *
     * @param isShow Whether to display / 是否显示
     */
    private fun hideOrShowLauncherIcon(isShow: Boolean) {
        packageManager?.setComponentEnabledSetting(
            ComponentName(packageName, "${BuildConfig.APPLICATION_ID}.Home"),
            if (isShow) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Get launcher icon state
     *
     * 获取启动器图标状态
     *
     * @return [Boolean] Whether to display / 是否显示
     */
    private val isLauncherIconShowing
        get() = packageManager?.getComponentEnabledSetting(
            ComponentName(packageName, "${BuildConfig.APPLICATION_ID}.Home")
        ) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /**
     * Refresh module status
     *
     * 刷新模块状态
     */
    private fun refreshModuleStatus() {
        binding.mainLinStatus.setBackgroundResource(
            when {
                YukiHookAPI.Status.isModuleActive -> R.drawable.bg_green_round
                else -> R.drawable.bg_dark_round
            }
        )
        binding.mainImgStatus.setImageResource(
            when {
                YukiHookAPI.Status.isModuleActive -> R.mipmap.ic_success
                else -> R.mipmap.ic_warn
            }
        )
        binding.mainTextStatus.text = getString(
            when {
                YukiHookAPI.Status.isModuleActive -> R.string.module_is_activated
                else -> R.string.module_not_activated
            }
        )
    }
}