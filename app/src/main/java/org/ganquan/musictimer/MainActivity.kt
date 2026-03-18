package org.ganquan.musictimer

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.ACTION_POWER_USAGE_SUMMARY
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import android.provider.Settings.ACTION_SETTINGS
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import org.ganquan.musictimer.comp.RadioGroup
import org.ganquan.musictimer.databinding.ActivityMainBinding
import org.ganquan.musictimer.tools.Apk
import org.ganquan.musictimer.tools.Broadcast
import org.ganquan.musictimer.tools.CoroutineScopeM
import org.ganquan.musictimer.tools.Files
import org.ganquan.musictimer.tools.OneTimeWorkerIntent
import org.ganquan.musictimer.tools.Permission
import org.ganquan.musictimer.tools.PermissionCode
import org.ganquan.musictimer.tools.Time
import org.ganquan.musictimer.tools.Utils
import org.ganquan.musictimer.tools.Utils.Companion.int2String
import java.io.File


class MainActivity : AppCompatActivity() {
    private var isPermissionRead: Boolean = false
    private var isPermissionWrite: Boolean = false
    private var isPermissionIgnoreBattery: Boolean = false
    private var isPermissionForegroundService: Boolean = false
    private var isPermissionAccessBackground: Boolean = false
    private var selectMusicName:String = ""
    private var countDownTimer: CountDownTimer? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var folderPath: String
    private var customPlayTime = NormalTimeAdapter.PLAY_TIME_INIT
    private var normalTimeList: MutableList<NormalTimeInfo> = mutableListOf(
        NormalTimeInfo(9,20,NormalTimeAdapter.PLAY_TIME_INIT),
        NormalTimeInfo(19,20,NormalTimeAdapter.PLAY_TIME_INIT))
    private var startTimeHour: Int = 0
    private var startTimeMunit: Int = 0
    private var playTime: Int = 0
    private var isStart: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initView()
        initPermission()
        initListener()
        initVersion()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permission = Permission(this, requestCode)
        when (requestCode) {
            PermissionCode.WRITE_EXTERNAL_STORAGE.data -> {
                isPermissionWrite = true
                initMusicPath()
            }
            PermissionCode.READ_MEDIA_AUDIO.data -> {
                if(permission.result(permissions,grantResults)) {
                    isPermissionRead = true
                    initMusicList()
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_music), Toast.LENGTH_LONG).show()
                    if(!isPermissionRead) binding.startBtn.text = getString(R.string.view_button_permission)
                }
            }
            PermissionCode.FOREGROUND_SERVICE.data -> isPermissionForegroundService = true
            PermissionCode.ACCESS_BACKGROUND_LOCATION.data -> {
                isPermissionAccessBackground = true
                if(!permission.result(permissions,grantResults)) {
                    binding.note.visibility = VISIBLE
                }
            }
        }
        initPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        CoroutineScopeM.cleanup()
        mainEnd()
    }

    /** 主程：开始 */
    @SuppressLint("SetTextI18n")
    private fun mainStart() {
        val now = Time.get()
        when (binding.mode.checkedRadioButtonId) {
            R.id.mode_normal -> {
                val info: NormalTimeInfo? =
                    normalTimeList.find { it -> !Time.isPass(it.hour, it.minute) }
                startTimeHour = (info?.hour) ?: -1
                startTimeMunit = (info?.minute) ?: 0
                playTime = (info?.time) ?: 0
            }

            R.id.mode_custom -> {
                startTimeHour = binding.startTime.hour
                startTimeMunit = binding.startTime.minute
                playTime = binding.playTime.value
            }
        }

        val startMinuteCount = startTimeHour * 60 + startTimeMunit
        val nowMinuteCount = now.hour * 60 + now.minute
        var playTime2 = if (nowMinuteCount == startMinuteCount) {
            playTime * 60 - now.second
        } else if (nowMinuteCount > startMinuteCount) {
            (playTime - (nowMinuteCount - startMinuteCount)) * 60
        } else {
            playTime * 60
        }

        if (playTime2 <= 0) {
            Toast.makeText(this, getString(R.string.toast_set_time), Toast.LENGTH_SHORT).show()
        } else {
            isStart = true

            initCountDown(playTime2)
            binding.startBtn.isEnabled = false
            binding.startBtn.text = "${int2String(startTimeHour)}:${int2String(startTimeMunit)} 开始播放"
            val delay = ((startMinuteCount - nowMinuteCount) * 60 - now.second).toLong()
            val playTime1 = (playTime * 60).toLong()

            // 保持cpu活跃
            wakeLock.acquire((delay+playTime1+5)*1000L)
            // 开启音乐服务
            startService(MusicIntent(this).setExtra(folderPath, selectMusicName))
            // 开户定时服务
            startService(OneTimeWorkerIntent(this).setExtra(delay,playTime1))
            // 保持亮屏
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** 主程：结束 */
    private fun mainEnd() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Broadcast.destroyLocal(this) { msg, info -> initReceiver(msg,info)}
        stopService(MusicIntent(this) as Intent)
        stopService(OneTimeWorkerIntent(this) as Intent)
        initMusicName()
        initCountDown()
        binding.startBtn.isEnabled = true
        binding.startBtn.text = getString(R.string.view_button_start)
        if(wakeLock.isHeld) wakeLock.release()
        isStart = false
    }

    private fun initView() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "$packageName::wakeLockTag")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            val normalTimeList1 = Utils.sharedPrefer(this, NormalTimeAdapter.SHARED_PREFER_KEY) as MutableList<*>
            if(normalTimeList1.isNotEmpty() && normalTimeList1[0] != "" && normalTimeList1[0] !is List<*>) {
                normalTimeList = normalTimeList1.map {
                    NormalTimeAdapter.toNormalTimeInfo(it as LinkedHashMap<String,Double>)
                } as MutableList<NormalTimeInfo>
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        binding.normalTimeList.layoutManager =
            GridLayoutManager(this, if(normalTimeList.size > NormalTimeAdapter.LINE_LIMIT) 2 else 1)
        initNormalModeList()
        binding.startTime.setIs24HourView(true)
        binding.playTime.minValue = 1
        binding.playTime.maxValue = 120
        binding.playTime.value = customPlayTime
        binding.mode.checkedRadioButtonId = R.id.mode_normal
        initNormalMode()
    }

    private fun initVersion() {
        val codeUrl = getString(R.string.code_url)

        Apk.checkVersion(
            this,
            codeUrl,
        ) { version ->
            if(version == "") return@checkVersion
            Apk.alertDialog(this, version, R.style.CustomDialog) {
                val apk = Apk(this)
                apk.down(codeUrl, version, getString(R.string.app_name)) { apkPath ->
                    apk.install(File(apkPath))
                }
            }
        }
    }

    private fun initPermission() {
        if(!isPermissionWrite) {
            isPermissionWrite = Permission(this, PermissionCode.WRITE_EXTERNAL_STORAGE.data).check()
            if (isPermissionWrite) initMusicPath()
            else return
        }

        if(!isPermissionRead) {
            isPermissionRead = Permission(this, PermissionCode.READ_MEDIA_AUDIO.data).check()
            if(isPermissionRead) initMusicList()
            else return
        }
        //开启白名单
        if(!isPermissionIgnoreBattery) {
            Permission(this, PermissionCode.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.data).check()
            isPermissionIgnoreBattery = true
        }
        if(!isPermissionForegroundService) {
            isPermissionForegroundService = Permission(this, PermissionCode.FOREGROUND_SERVICE.data).check()
        }
        //开启【允许后台活动】
        if(!isPermissionAccessBackground) {
            isPermissionAccessBackground = Permission(this, PermissionCode.ACCESS_BACKGROUND_LOCATION.data).check()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initMusicPath() {
        val name = getString(R.string.view_music_path_name)
        val path = Files.createFolder(Files.getMusicFile(name))
        if(path == "") {
            folderPath = Files.getMusicFile().path
        } else {
            binding.musicPath.text = "${getString(R.string.view_music_path)}/${name}"
            folderPath = path
        }
    }

    private fun initMusicList(): Boolean {
        if(binding.musicList.adapter != null) return false

        val list:List<File> = Files.getList(folderPath.toString())
        if(!list.isNotEmpty()) {
            return false
        }

        val defaultName = getString(R.string.view_music_default)
        var names = list.map { it.nameWithoutExtension }.toMutableList()
        names.add(0, defaultName)

        val adapter = ArrayAdapter(this, R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.musicList.adapter = adapter
        binding.musicList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectMusicName = parent.getItemAtPosition(position).toString()
                if(selectMusicName == defaultName) selectMusicName = ""
                mainEnd()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectMusicName = ""
                mainEnd()
            }
        }

        binding.startBtn.text = getString(R.string.view_button_start)
        return true
    }

    private fun initMusicName(name: String = "") {
        if(selectMusicName != "") binding.musicName.visibility = GONE
        else {
            binding.musicName.text = name
            binding.musicName.visibility = if (name == "") GONE else VISIBLE
        }
    }

    private fun initCountDown(second: Int = 0) {
        if (second != 0) {
            countDownTimer = object : CountDownTimer((second*1000).toLong(), 1000) {
                @SuppressLint("SetTextI18n")
                override fun onTick(millisUntilFinished: Long) {
                    val hms: Triple<Int,Int,Int> = Time.toHourMinuteSecond(millisUntilFinished)
                    binding.startBtn.text =
                        "${int2String(hms.second)}:${int2String(hms.third)}  ${this@MainActivity.getString(R.string.view_button_end)}"
                }

                override fun onFinish() {}
            }
        } else {
            countDownTimer?.cancel()
            countDownTimer = null
        }
    }

    private fun initNormalMode(isEdit: Boolean = false) {
        if(isEdit) {
            initTimeView(NormalTimeAdapter.PLAY_TIME_INIT)
            binding.modeCustomDetail.visibility = VISIBLE
            binding.normalTimeEdit.text = getString(R.string.view_time_btn_done)
            binding.startBtn.text = getString(R.string.view_button_add)
            binding.modeCustom.visibility = GONE
            initNormalModeList(VISIBLE)
        } else {
            binding.modeCustomDetail.visibility = GONE
            binding.normalTimeEdit.text = getString(R.string.view_time_btn_edit)
            binding.startBtn.text = getString(R.string.view_button_start)
            binding.modeCustom.visibility = VISIBLE
            initNormalModeList(GONE)
        }

        binding.normalTimeList.visibility = VISIBLE
        binding.normalTimeEdit.visibility = VISIBLE
        binding.startBtn.isEnabled = true
    }

    private fun initCustomMode() {
        initTimeView()
        binding.normalTimeList.visibility = GONE
        binding.normalTimeEdit.visibility = GONE
        binding.modeCustomDetail.visibility = VISIBLE
        binding.startBtn.isEnabled = true
        binding.startBtn.text = getString(R.string.view_button_start)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initNormalModeList(isDeleteVisibility: Int = GONE) {
        normalTimeList.forEach { it -> it.delBtnVisibility = isDeleteVisibility}
        normalTimeList.sortWith(compareBy<NormalTimeInfo> { it.hour }.thenBy { it.minute })
        binding.normalTimeList.adapter = NormalTimeAdapter(normalTimeList)
        binding.normalTimeList.adapter?.notifyDataSetChanged()
    }

    private fun initTimeView(pTime: Int = 0) {
        val now = Time.get()
        binding.startTime.hour = now.hour
        binding.startTime.minute = now.minute
        binding.playTime.value = pTime or customPlayTime
    }

    @SuppressLint("BatteryLife")
    private fun initListener() {
        binding.startBtn.setOnClickListener {
            when (binding.startBtn.text.split(" ").last()) {
                getString(R.string.view_button_start) -> mainStart()
                getString(R.string.view_button_permission) -> initPermission()
                getString(R.string.view_button_add) -> handleAddNormalTime()
                getString(R.string.view_button_end) -> mainEnd()
            }
        }

        binding.startTime.setOnTimeChangedListener { _, h, m ->
            if(isStart) mainEnd()
        }

        binding.playTime.setOnValueChangedListener { _, oldV, newV ->
            if(binding.mode.checkedRadioButtonId == R.id.mode_custom) customPlayTime = newV
            if(isStart) mainEnd()
        }

        binding.normalTimeEdit.setOnClickListener {
            if(isStart) mainEnd()
            when (binding.normalTimeEdit.text) {
                getString(R.string.view_time_btn_edit) -> initNormalMode(true)
                getString(R.string.view_time_btn_done) -> initNormalMode()
            }
        }

        binding.mode.setOnCheckedChangeListener(object : RadioGroup.OnCheckedChangeListener {
            override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
                if(checkedId == binding.mode.checkedRadioButtonId) return
                binding.mode.check(checkedId)
                when (checkedId) {
                    R.id.mode_normal -> initNormalMode()
                    R.id.mode_custom -> initCustomMode()
                }
                mainEnd()
            }
        })

        binding.noteBtn.setOnClickListener {
            Permission(this, PermissionCode.ACCESS_BACKGROUND_LOCATION.data)
                .openSetting(if(Permission.isVivoDevice()) ACTION_POWER_USAGE_SUMMARY else ACTION_SETTINGS)
        }

        Broadcast.receiveLocal (this) { msg, info -> initReceiver(msg, info) }
    }

    private fun initReceiver(msg: String, info: String) {
        when (msg) {
            "start worker" -> handlerPlaying()
            "end worker" -> mainEnd()
            "new music" -> initMusicName(info)
        }
    }

    private fun handleAddNormalTime() {
        if(normalTimeList.size >= NormalTimeAdapter.ITEM_TOTAL) {
            Toast.makeText(this, getString(R.string.toast_normal_time_limit), Toast.LENGTH_SHORT).show()
            return
        }
        val newInfo = NormalTimeInfo(
            binding.startTime.hour,
            binding.startTime.minute,
            binding.playTime.value,
            VISIBLE
        )
        val isExists = normalTimeList.find { it.hour == newInfo.hour && it.minute == newInfo.hour }
        if(isExists != null) {
            Toast.makeText(this, getString(R.string.toast_normal_time_exists), Toast.LENGTH_SHORT).show()
        } else {
            if(normalTimeList.size >= NormalTimeAdapter.LINE_LIMIT)
                (binding.normalTimeList.layoutManager as GridLayoutManager).setSpanCount(2)
            normalTimeList.add(newInfo)
            initNormalModeList(VISIBLE)
        }
    }

    private fun handlerPlaying() {
        startService(MusicIntent(this).setAction(MusicService.ACTION_PLAY))
        countDownTimer?.start()
        binding.startBtn.isEnabled = true
        binding.startBtn.text = getString(R.string.view_button_end)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}