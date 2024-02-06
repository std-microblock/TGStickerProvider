@file:Suppress("SetTextI18n", "Deprecation")

package cc.microblock.TGStickerProvider.ui.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
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
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.widget.doOnTextChanged
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
import com.arthenica.ffmpegkit.FFmpegKit
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.File
import kotlin.concurrent.thread


data class StickerState(var highQuality: Int, var lowQuality: Int, var all: Int)
data class PreviewInfo(var type: String, var url: String)
data class StickerInfo(
    val name: String,
    val id: String,
    val hash: String,
    val remoteState: StickerState,
    val syncedState: StickerState,
    val all: Int,
    val type: String,
    val preview: PreviewInfo
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
        val imageView: ShapeableImageView = view.findViewById(R.id.imageView)
        val videoView: VideoView = view.findViewById(R.id.videoView)
        val videoCard: CardView = view.findViewById(R.id.videoCard)
        val unsupported: TextView = view.findViewById(R.id.unsupported)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false)
        return ViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = stickerList[position]
        holder.name.text = s.name
        holder.id.text = s.id
        holder.syncedAll.text = s.syncedState.all.toString();
        holder.syncedStatus.text = "低清 ${s.syncedState.lowQuality}\n高清 ${s.syncedState.highQuality}"
        holder.exportedAll.text = s.remoteState.all.toString()
        holder.exportedStatus.text = "低清 ${s.remoteState.lowQuality}\n高清 ${s.remoteState.highQuality}"
        holder.totalAll.text = s.all.toString();

        when (s.type) {
            "image/webp" -> {
                Glide.with(act)
                    .load(s.preview.url)
                    .error(ColorDrawable(Color.RED))
                    .into(holder.imageView)
                holder.videoCard.visibility = View.GONE
                holder.imageView.visibility = View.VISIBLE
                holder.videoView.stopPlayback()
                holder.unsupported.visibility = View.GONE
                holder.rmBtn.visibility = View.VISIBLE
                holder.syncBtn.visibility = View.VISIBLE
            }
            "video/webm" -> {
                holder.videoView.setVideoPath(s.preview.url)
                holder.videoView.start()
                holder.videoView.setOnPreparedListener {
                    it.isLooping = true
                    it.setVolume(0f, 0f)
                }
                holder.videoView.setOnErrorListener { mp, what, extra ->
                    YLog.error("VideoView error: $what, $extra")
                    true
                }

                holder.videoCard.visibility = View.VISIBLE
                holder.imageView.visibility = View.GONE
                holder.unsupported.visibility = View.GONE
                holder.rmBtn.visibility = View.VISIBLE
                holder.syncBtn.visibility = View.VISIBLE
            }
            else -> {
                holder.imageView.visibility = View.GONE
                holder.videoCard.visibility = View.GONE
                holder.videoView.stopPlayback()
                holder.rmBtn.visibility = View.GONE
                holder.syncBtn.visibility = View.GONE
                holder.unsupported.visibility = View.VISIBLE
                holder.unsupported.text = "不支持的表情包"
            }
        }

        holder.syncBtn.setOnClickListener {
            val pd = ProgressDialog(act)
            var failCount = 0
            var failReasons = ""
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

                    val existing = File(syncedFolder).listFiles()?.filter { it.name.startsWith(id) }
                        ?: emptyList()

                    when(remoteFile.extension) {
                        "webp"->{
                            try {
                                val outPathPng = "${syncedFolder}/${nameWithoutExt}.png"
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

                                for (file in existing) {
                                    if(file.extension == "webp")
                                        file.delete()
                                    if(file.extension == "png" && file.name != "${nameWithoutExt}.png")
                                        file.delete()
                                }
                            } catch (e: Exception) {
                                YLog.error("Error while converting webp to png", e)
                                failReasons += "$id: Error while converting webp to png: ${e.message}\n"
                                failCount++
                            }
                        }
                        "webm"->{
                            // encode to gif
                            val outPath = "${syncedFolder}/${nameWithoutExt}.gif"
                            if (!File(outPath).exists()) {

                                val session = FFmpegKit.execute(
                                    if(act.useHighQualityGif)
                                        "-i ${remoteFile.absolutePath} -lavfi split[v],palettegen,[v]paletteuse -f gif ${outPath}"
                                    else
                                        "-i ${remoteFile.absolutePath} -vf scale=320:-1 -r 10 -f gif ${outPath}"
                                )
                                if (session.returnCode.isValueSuccess) {
                                    YLog.info("FFmpegKit: ${session.command} finished successfully")
                                } else {
                                    YLog.error("FFmpegKit: ${session.command} failed with state ${session.state} and rc ${session.returnCode}")
                                    failReasons += "$id: FFmpegKit: ${session.command} failed with state ${session.state} and rc ${session.returnCode}\n"
                                    failCount++
                                }
                            }
                        }
                    }

                    act.runOnUiThread { pd.setMessage("正在同步 ${index}/${remoteFileList.size}") }
                }
                act.runOnUiThread { pd.setMessage("正在更新列表") }
                act.updateStickerList()
                act.runOnUiThread { pd.dismiss() }

                if (failCount > 0) {
                    act.runOnUiThread {
                        AlertDialog.Builder(act)
                            .setTitle("有 $failCount 个表情包同步失败")
                            .setMessage(failReasons)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton("确定", null).show()
                    }
                }
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

    var filter = ""
    var useHighQualityGif = false
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate() {
        refreshModuleStatus()
        requestPermission()
        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)
        binding.button2.setOnClickListener {
            binding.tips.visibility = View.GONE
            binding.manage.visibility = View.VISIBLE
        }
        binding.gifQualitySwitch.setOnCheckedChangeListener { _, isChecked ->
            useHighQualityGif = isChecked
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

        binding.searchBar.doOnTextChanged() { text, _, _, _ ->
            filter = text.toString()
            (binding.stickerManageView.adapter as RecyclerAdapterStickerList).stickerList =
                stickerList.value?.filter {
                    it.name.contains(filter, ignoreCase = true) || it.id.contains(filter, ignoreCase = true)
                            || it.type.contains(filter, ignoreCase = true)
                } ?: listOf()
            binding.stickerManageView.adapter?.notifyDataSetChanged()
        }

        binding.stickerManageView.layoutManager = LinearLayoutManager(this)
        binding.stickerManageView.adapter = RecyclerAdapterStickerList(this)
        binding.stickerManageView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if(newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    Glide.with(this@MainActivity).resumeRequests()
                } else {
                    Glide.with(this@MainActivity).pauseRequests()
                }
                super.onScrollStateChanged(recyclerView, newState)
            }
        })
        try {
            binding.stickerManageView.javaClass.getDeclaredField("mMaxFlingVelocity").let {
                it.isAccessible = true
                it.set(binding.stickerManageView, 1000)
            }
        } catch (e: Exception) {
            YLog.error("Error while setting max fling velocity", e)
        }

        stickerList.observe(this) {
            runOnUiThread {
                (binding.stickerManageView.adapter as RecyclerAdapterStickerList).stickerList = it.filter {
                    it.name.contains(filter, ignoreCase = true) || it.id.contains(filter, ignoreCase = true)
                            || it.type.contains(filter, ignoreCase = true)
                }
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
                val type = if(lines.size > 3) lines[3] else "unknown"

                if (stickerList.any { it.hash == hash }) continue

                val remoteState = StickerState(0, 0, 0)
                val syncedState = StickerState(0, 0, 0)

                File("$destDataPath/tgSync_$id").listFiles()?.forEach {
                    if (it.name.endsWith("_low.webp")) {
                        remoteState.lowQuality++
                    } else if (it.name.endsWith("_high.webp") || it.name.endsWith("_high.webm")) {
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

                val files = File("$destDataPath/tgSync_${id}").listFiles()?: emptyArray();
                val preview = if (files.isNotEmpty()) {
                    val it = files[0]
                    if (it.extension == "webp") {
                        PreviewInfo("webp", it.absolutePath)
                    } else if(it.extension == "webm") {
                        PreviewInfo("webm", it.absolutePath)
                    } else
                    {
                        PreviewInfo("unknown", "")
                    }
                } else {
                    PreviewInfo("unknown", "")
                }

                stickerList.add(StickerInfo(name, id, hash, remoteState, syncedState, allSize, type, preview))
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