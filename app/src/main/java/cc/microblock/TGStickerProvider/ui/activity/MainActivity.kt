@file:Suppress("SetTextI18n", "Deprecation")

package cc.microblock.TGStickerProvider.ui.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.microblock.TGStickerProvider.BuildConfig
import cc.microblock.TGStickerProvider.R
import cc.microblock.TGStickerProvider.databinding.ActivityMainBinding
import cc.microblock.TGStickerProvider.destDataPath
import cc.microblock.TGStickerProvider.exposedPath
import cc.microblock.TGStickerProvider.nomediaPath2
import cc.microblock.TGStickerProvider.realDataPath
import cc.microblock.TGStickerProvider.stickerDataPath
import cc.microblock.TGStickerProvider.tgspDataPath
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

class RecyclerAdapterStickerList(private val act: MainActivity) :
    RecyclerView.Adapter<RecyclerAdapterStickerList.ViewHolder>() {
    var stickerList = listOf<StickerInfo>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.sticker_name)
        val id: TextView = view.findViewById(R.id.sticker_id)
        val syncedAll: TextView = view.findViewById(R.id.syncedAll)
        val syncedStatus: TextView = view.findViewById(R.id.syncedStatus)
        val exportedAll: TextView = view.findViewById(R.id.exportedAll)
        val exportedStatus: TextView = view.findViewById(R.id.exportedStatus)
        val totalAll: TextView = view.findViewById(R.id.totalAll)
        val syncBtn: Button = view.findViewById(R.id.syncBtn)
        val rmBtn: MaterialTextView = view.findViewById(R.id.rmBtn)
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
        holder.syncedAll.text = s.syncedState.all.toString();
        holder.syncedStatus.text = "低清 ${s.syncedState.lowQuality}\n高清 ${s.syncedState.highQuality}"
        holder.exportedAll.text = s.remoteState.all.toString()
        holder.exportedStatus.text = "低清 ${s.remoteState.lowQuality}\n高清 ${s.remoteState.highQuality}"
        holder.totalAll.text = s.all.toString();

        holder.syncBtn.setOnClickListener {
            val pd = ProgressDialog(act)
            pd.setMessage("正在同步")
            pd.show()
            pd.setCancelable(false)
            thread(true) {
                val remoteFolder = "$destDataPath/tgSync_${s.id}"
                val syncedFolder = "$realDataPath/tgSync_${s.id}"
                File(syncedFolder).mkdirs()

                if (!File(nomediaPath2).exists()) {
                    File(nomediaPath2).createNewFile()
                }

                val remoteFileList = File(remoteFolder).listFiles() ?: emptyArray()

                for ((index, remoteFile) in remoteFileList.withIndex()) {
                    val nameWithoutExt = remoteFile.name.substringBefore(".")
                    val id = nameWithoutExt.substringBefore("_")
                    val outPathPng = "${syncedFolder}/${nameWithoutExt}.png"
                    // val outPathGif = "${syncedFolder}/${nameWithoutExt}.gif"
                    val outPathWebp = "${syncedFolder}/${nameWithoutExt}.webp"

                    val existing = File(syncedFolder).listFiles()?.filter { it.name.startsWith(id) }
                        ?: emptyList()
                    if (existing.isEmpty() || existing[0].name.endsWith(".webp")) {
                        if (remoteFile.name.endsWith(".webp")) {
                            val decoder = ImageDecoder.createSource(remoteFile)
                            val bitmap = ImageDecoder.decodeBitmap(decoder)
                            val out = File(outPathPng)
                            out.outputStream().buffered().use {
                                bitmap.compress(
                                    android.graphics.Bitmap.CompressFormat.PNG,
                                    100,
                                    it
                                )
                            }

                            File(outPathWebp).delete()

                            for (file in existing) {
                                file.delete()
                            }
                        } else {
                            remoteFile.copyTo(File("${syncedFolder}/${remoteFile.name}"), true)
                        }
                    }

                    act.runOnUiThread { pd.setMessage("正在同步 ${index}/${remoteFileList.size}") }
                }
                act.runOnUiThread { pd.setMessage("正在更新列表") }
                act.updateStickerList()
                act.runOnUiThread { pd.dismiss() }
            }
        }

        holder.rmBtn.setOnClickListener {
            // dialog confirm
            AlertDialog.Builder(act)
                .setTitle("删除已同步的表情包集 ${s.name}")
                .setMessage("你将删除已同步的表情包集 ${s.name}，这将会删除 ${s.syncedState.all} 个表情包文件，是否继续？")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("删除") { _, _ ->
                    val pd = ProgressDialog(act)
                    pd.setMessage("正在删除")
                    pd.show()
                    pd.setCancelable(false)
                    Thread {
                        // val remoteFolder = "${destDataPath}tgSync_${s.id}"
                        // File(remoteFolder).deleteRecursively()
                        File("$realDataPath/tgSync_${s.id}").deleteRecursively()
                        act.runOnUiThread { pd.setMessage("正在更新列表") }
                        act.updateStickerList()
                        act.runOnUiThread { pd.dismiss() }
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

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 先判断有没有权限
            if (Environment.isExternalStorageManager()) {
                onGainedPermission()
                return
            }
            Toast.makeText(
                this,
                "请授予插件完全文件访问权限，以同步表情包~",
                Toast.LENGTH_LONG
            ).show()
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:" + this@MainActivity.packageName)
            }, 2339)
        }
    }

    private fun onGainedPermission() {
        Toast.makeText(
            this,
            "权限获取成功，可以同步表情包啦~",
            Toast.LENGTH_LONG
        ).show()

        updateStickerList()
    }

    @Deprecated("Deprecated in Java")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2339) {
            if (Environment.isExternalStorageManager()) {
                onGainedPermission()
                return
            }
            Toast.makeText(
                this,
                "请授予插件完全文件访问权限，以同步表情包~",
                Toast.LENGTH_LONG
            ).show()
            requestPermission()
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate() {
        refreshModuleStatus()
        requestPermission()
        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)
        binding.button2.setOnClickListener {
            binding.tips.visibility = View.GONE
            binding.manage.visibility = View.VISIBLE
        }

        binding.resetBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清除已同步的表情包集缓存")
                .setMessage("你将清除导出表情包集的缓存，这不会影响已同步的表情包，是否继续？")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("删除") { _, _ ->
                    val pd = ProgressDialog(this).apply {
                        setMessage("正在清除")
                        show()
                        setCancelable(false)
                    }
                    thread(true) {
                        File(exposedPath).deleteRecursively()
                        this.stickerList.postValue(listOf())
                        pd.dismiss()
                    }
                }
                .setNegativeButton("算了", null).show()
        }

        binding.refreshBtn.setOnClickListener {
            val pd = ProgressDialog(this).apply {
                setMessage("正在更新")
                show()
                setCancelable(false)
            }
            thread(true) {
                updateStickerList()
                pd.dismiss()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "共刷新${this.stickerList.value?.size ?: 0}个贴纸包",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.stickerManageView.layoutManager = LinearLayoutManager(this)
        binding.stickerManageView.adapter = RecyclerAdapterStickerList(this)

        stickerList.observe(this) {
            runOnUiThread {
                (binding.stickerManageView.adapter as RecyclerAdapterStickerList).stickerList = it
                binding.stickerManageView.adapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshModuleStatus()
    }

    private val stickerList by lazy {
        MutableLiveData<List<StickerInfo>>(listOf())
    }

    fun updateStickerList() {
        if (!File(tgspDataPath).exists()) {
            this.stickerList.postValue(listOf())
            return
        }

        val ignoreIds = try {
            File("$exposedPath/ignore.txt").run {
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
        } catch (e: Exception) {
            YLog.error("Error while reading ignore.txt", e)
            emptyList()
        }
        YLog.info("ignoreIds: [${ignoreIds.joinToString { "`$it`" }}]")

        val stickerList = mutableListOf<StickerInfo>()

        for (file in File(stickerDataPath).listFiles()!!) {
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

                File("$destDataPath/tgSync_$id").listFiles()?.forEach {
                    if (it.name.endsWith("_low.webp")) {
                        remoteState.lowQuality++
                    } else if (it.name.endsWith("_high.webp")) {
                        remoteState.highQuality++
                    }

                    remoteState.all++
                }

                File("$realDataPath/tgSync_${id}").listFiles()?.forEach {
                    if (it.name.contains("_low.")) {
                        syncedState.lowQuality++
                    } else if (it.name.contains("_high.")) {
                        syncedState.highQuality++
                    }

                    syncedState.all++
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
     * Refresh module status
     *
     * 刷新模块状态
     */
    private fun refreshModuleStatus() {
        binding.mainLinStatus.setBackgroundResource(
            if (YukiHookAPI.Status.isModuleActive) R.drawable.bg_green_round
            else R.drawable.bg_dark_round
        )
        binding.mainImgStatus.setImageResource(
            if (YukiHookAPI.Status.isModuleActive) R.mipmap.ic_success
            else R.mipmap.ic_warn
        )
        binding.mainTextStatus.text = getString(
            if (YukiHookAPI.Status.isModuleActive) R.string.module_is_activated
            else R.string.module_not_activated
        )
    }
}