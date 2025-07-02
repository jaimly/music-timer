package org.ganquan.musictimer

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.Manifest.permission.WAKE_LOCK
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import org.ganquan.musictimer.comp.RadioGroup
import org.ganquan.musictimer.databinding.ActivityMainBinding
import org.ganquan.musictimer.tools.Broadcast
import org.ganquan.musictimer.tools.OneTimeWorker
import org.ganquan.musictimer.tools.Permission
import org.ganquan.musictimer.tools.Utils
import java.io.File

class MainActivity : AppCompatActivity() {
    private val permission = Permission(this)
    private val oneTimeWorker: OneTimeWorker = OneTimeWorker(this)
    private var isReadPermission: Boolean = false
    private var isWakeLockPermission: Boolean = false
    private var selectMusicName:String = ""
    private lateinit var binding: ActivityMainBinding
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var folderPath: String
    private var customPlayTime = 10
    private var normalTimeList: MutableList<List<Int>> =
        mutableListOf(listOf(9,20,10), listOf(19,20,10))
    private var startTimeHour = normalTimeList[0][0]
    private var startTimeMunit: Int = normalTimeList[0][0]
    private var playTime = normalTimeList[0][1]
    private var isStart: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initView()
        initPermission()
        initListen()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val type = permission.result(requestCode,grantResults)
        when (type) {
            in READ_MEDIA_AUDIO,READ_EXTERNAL_STORAGE -> {
                isReadPermission = true
                initMusicList()
            }
            WRITE_EXTERNAL_STORAGE -> initFolder()
            WAKE_LOCK -> {
                isWakeLockPermission = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        end()
    }

    private fun initView() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val normalTimeList1: MutableList<List<Int>> = Utils.sharedPrefer(this, "normalTimeList", "MutableList") as MutableList<List<Int>>
        if(normalTimeList1.isNotEmpty()) normalTimeList = normalTimeList1.map { it.map { it.toInt() }} as MutableList<List<Int>>
        binding.normalTimeList.layoutManager = LinearLayoutManager(this)
        binding.normalTimeList.adapter = NormalTimeAdapter(normalTimeList)
        binding.startTime.setIs24HourView(true)
        binding.playTime.minValue = 1
        binding.playTime.maxValue = 120
        binding.playTime.value = customPlayTime
        binding.startBtn.isEnabled = true
        binding.mode.check(R.id.mode_normal)
    }

    private fun initPermission() {
        initFolder(true)
        initMusicList()
        isWakeLockPermission = permission.check(WAKE_LOCK)
        if(!isReadPermission) binding.startBtn.text = getString(R.string.view_button_permission)
    }

    @SuppressLint("SetTextI18n")
    private fun initFolder(isCheck: Boolean = false) {
        val name = getString(R.string.view_music_path_name)
        val path = Utils.createFolder(name, Environment.DIRECTORY_MUSIC)
        if(path == "") {
            if(isCheck) {
                val isWrite = permission.check(WRITE_EXTERNAL_STORAGE)
                if (isWrite) initFolder()
            } else {
                folderPath = "${Environment.getExternalStorageDirectory()}/${Environment.DIRECTORY_MUSIC}"
            }
        } else {
            binding.musicPath.text = "${getString(R.string.view_music_path)}/${name}"
            folderPath = path
        }
    }

    private fun initListen() {
        binding.startBtn.setOnClickListener {
            when (binding.startBtn.text) {
                getString(R.string.view_button_start) -> start()
                getString(R.string.view_button_end) -> end()
                getString(R.string.view_button_permission) -> initPermission()
                getString(R.string.view_button_save) -> addNormalTime()
            }
        }

        binding.startTime.setOnTimeChangedListener { _, h, m ->
            if(isStart) end()
        }

        binding.playTime.setOnValueChangedListener { _, oldV, newV ->
            if(binding.mode.checkedRadioButtonId == R.id.mode_custom) customPlayTime = newV
            if(isStart) end()
        }

        binding.normalTimeAdd.setOnClickListener {
            if(isStart) end()
            when (binding.normalTimeAdd.text) {
                getString(R.string.view_time_btn_add) -> {
                    setCustomTime(10)
                    binding.modeCustomDetail.visibility = VISIBLE
                    binding.normalTimeAdd.text = getString(R.string.view_time_btn_cancel)
                    binding.startBtn.text = getString(R.string.view_button_save)
                    binding.modeCustom.visibility = GONE
                }
                getString(R.string.view_time_btn_cancel) -> {
                    binding.modeCustomDetail.visibility = GONE
                    binding.normalTimeAdd.text = getString(R.string.view_time_btn_add)
                    binding.startBtn.text = getString(R.string.view_button_start)
                    binding.modeCustom.visibility = VISIBLE
                }
            }
        }

        binding.mode.setOnCheckedChangeListener(object : RadioGroup.OnCheckedChangeListener {
             override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
                 if(checkedId == binding.mode.checkedRadioButtonId) return
                 binding.mode.check(checkedId)
                 when (checkedId) {
                     R.id.mode_normal -> {
                         binding.normalTimeList.visibility = VISIBLE
                         binding.normalTimeAdd.visibility = VISIBLE
                         binding.modeCustomDetail.visibility = GONE
                     }
                     R.id.mode_custom -> {
                         setCustomTime()
                         binding.normalTimeList.visibility = GONE
                         binding.normalTimeAdd.visibility = GONE
                         binding.modeCustomDetail.visibility = VISIBLE
                     }
                 }
                 end()
             }
        })

        Broadcast.receiveLocal (this) { msg, info -> broadcastReceiveHandler(msg, info) }
    }

    private fun initMusicList() {
        if(binding.musicList.adapter != null) return

        val list = getMusicList()
        if(list == null) {
            if(!isReadPermission) binding.startBtn.text = getString(R.string.view_button_permission)
            return
        }

        binding.startBtn.text = getString(R.string.view_button_start)

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
                end()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectMusicName = ""
                end()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun start() {
        isStart = true

        val now = Utils.getTime()
        when (binding.mode.checkedRadioButtonId) {
            R.id.mode_normal -> {
                val list: List<Int>? = normalTimeList.find {it -> !isPass(it[0],it[1])}
                startTimeHour = (list?.get(0)) ?: -1
                startTimeMunit = (list?.get(1)) ?: 0
                playTime = (list?.get(2)) ?: 0
            }
            R.id.mode_custom -> {
                startTimeHour = binding.startTime.hour
                startTimeMunit = binding.startTime.minute
                playTime = binding.playTime.value
            }
        }

        val startMinuteCount = startTimeHour * 60 + startTimeMunit
        val nowMinuteCount = now.hour * 60 + now.minute

        if (nowMinuteCount > startMinuteCount) {
            Toast.makeText(this, getString(R.string.toast_set_time), Toast.LENGTH_SHORT).show()
        } else {
            binding.startBtn.isEnabled = false
            binding.startBtn.text = "${Utils.int2String(startTimeHour)}:${Utils.int2String(startTimeMunit)} 开始播放"
            val delay = ((startMinuteCount - nowMinuteCount) * 60 - now.second).toLong()
            val playTime1 = (playTime * 60).toLong()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            wakeLock.acquire((delay+playTime1+5)*1000L)

            val intent = initIntent()
            intent.putExtra("folder_path", folderPath)
            intent.putExtra("music_name", selectMusicName)
            startForegroundService(intent)

            oneTimeWorker.request(
                playTime1,
                delay
            )
        }
    }

    private fun end() {
        oneTimeWorker.cancel()
        reSet()
    }

    private fun playing() {
        startForegroundService(initIntent("ACTION_PLAY"))

        binding.startBtn.isEnabled = true
        binding.startBtn.text = getString(R.string.view_button_end)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun reSet() {
        Broadcast.destroyLocal(this) { msg, info -> broadcastReceiveHandler(msg,info)}
        stopService(initIntent())
        binding.startBtn.isEnabled = true
        binding.startBtn.text = getString(R.string.view_button_start)
        binding.normalTimeAdd.text = getString(R.string.view_time_btn_add)
        binding.modeCustom.visibility = VISIBLE
        binding.musicName.visibility = GONE
        binding.musicName.text = ""
        if(wakeLock.isHeld) wakeLock.release()
        isStart = false
    }

    private fun setCustomTime(pTime: Int = 0) {
        val now = Utils.getTime()
        binding.startTime.hour = now.hour
        binding.startTime.minute = now.minute
        binding.playTime.value = pTime or customPlayTime
    }

    private fun setMusicName(name: String = "") {
        binding.musicName.text = name
        if(selectMusicName == "") binding.musicName.visibility = VISIBLE
    }

    private fun addNormalTime() {
        if(normalTimeList.size >= 10) {
            Toast.makeText(this, getString(R.string.toast_normal_time_limit), Toast.LENGTH_SHORT).show()
            return
        }
        val newList = listOf(
            binding.startTime.hour,
            binding.startTime.minute,
            binding.playTime.value
        )
        val isExists = normalTimeList.find { it.containsAll(newList) }
        if(isExists != null) {
            Toast.makeText(this, getString(R.string.toast_normal_time_exists), Toast.LENGTH_SHORT).show()
        } else {
            normalTimeList.add(newList)
            normalTimeList.sortWith(compareBy<List<Int>> { it[0] }.thenBy { it[1] })
            binding.normalTimeList.adapter = NormalTimeAdapter(normalTimeList)
        }
    }

    private fun getMusicList(): List<File>? {
        if(!isReadPermission) {
            isReadPermission = permission.check(READ_MEDIA_AUDIO)
            if(!isReadPermission) return null
        }

        val list:List<File> = Utils.getFileList(folderPath.toString())
        if(list.isNotEmpty()) return list
        Toast.makeText(this, getString(R.string.toast_no_music), Toast.LENGTH_LONG).show()
        return null
    }

    private fun broadcastReceiveHandler(msg: String, info: String) {
        when (msg) {
            "start worker" -> playing()
            "end worker" -> reSet()
            "new music" -> setMusicName(info)
        }
    }

    private fun initIntent(action: String = ""): Intent {
        val intent = Intent(this, MusicService::class.java)
        if(action != "") intent.action = action
        return intent
    }

    private fun isPass(hour: Int, munit: Int): Boolean {
        val now = Utils.getTime()
        val startMinuteCount = hour * 60 + munit
        val nowMinuteCount = now.hour * 60 + now.minute

        return nowMinuteCount > startMinuteCount
    }
}