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
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.ganquan.musictimer.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {
    private val permission = Permission(this)
    private val musicWorker: MusicWorker = MusicWorker(this)
    private var isReadPermission: Boolean = false
    private var isWakeLockPermission: Boolean = false
    private var selectMusicName:String = ""
    private lateinit var binding: ActivityMainBinding
    private lateinit var powerManager:PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var folderPath: String

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
            WRITE_EXTERNAL_STORAGE -> createFolder()
            WAKE_LOCK -> {
                isWakeLockPermission = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        endTimer()
    }

    private fun initView() {
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startTime.setIs24HourView(true)
        binding.startTime.hour = 9
        binding.startTime.minute = 20
        binding.playTime.minValue = 1
        binding.playTime.maxValue = 120
        binding.playTime.value = 10
        binding.startBtn.isEnabled = true
    }

    private fun initPermission() {
        createFolder(true)
        initMusicList()
        isWakeLockPermission = permission.check(WAKE_LOCK)
        if(!isReadPermission) binding.startBtn.text = getString(R.string.view_button_permission)
    }

    private fun initListen() {
        binding.startBtn.setOnClickListener {
            when (binding.startBtn.text) {
                getString(R.string.view_button_start) -> startTimer()
                getString(R.string.view_button_end) -> endTimer()
                getString(R.string.view_button_permission) -> initPermission()
            }
        }

        binding.startTime.setOnTimeChangedListener { _, h, m ->
            endTimer()
        }

        binding.playTime.setOnValueChangedListener { _, oldV, newV ->
            endTimer()
        }

        binding.mode.setOnCheckedChangeListener { group, checkedId ->
            val now = Utils.getTime()
            binding.playTime.value = 10
            when (checkedId) {
                R.id.mode_normal -> {
                    binding.startTime.hour = if( now.hour < 12 ) 9 else 19
                    binding.startTime.minute = 20
                    binding.playTime.value = 10
                    binding.modeCustomDetail.visibility = View.GONE
                }
                R.id.mode_custom -> {
                    binding.startTime.hour = now.hour
                    binding.startTime.minute = now.minute
                    binding.modeCustomDetail.visibility = View.VISIBLE
                }
            }
            endTimer()
        }
        binding.mode.check(R.id.mode_normal)

        Broadcast.receiveLocal (this) { msg -> broadcastReceiveHandler(msg)}
    }

    private fun broadcastReceiveHandler(msg: String) {
        when (msg) {
            "start worker" -> playing()
            "end worker" -> restart()
        }
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
        var names = list.map { it.name }.toMutableList()
        names.add(0, defaultName)
        val adapter = ArrayAdapter(this, R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.musicList.adapter = adapter

        binding.musicList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectMusicName = parent.getItemAtPosition(position).toString()
                if(selectMusicName == defaultName) selectMusicName = ""
                endTimer()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectMusicName = ""
                endTimer()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startTimer() {
        val startTimeH = binding.startTime.hour
        val startTimeM = binding.startTime.minute
        val playTime = binding.playTime.value
        val now = Utils.getTime()
        val startMinuteCount = startTimeH * 60 + startTimeM
        val nowMinuteCount = now.hour * 60 + now.minute

        if (nowMinuteCount > startMinuteCount) {
            Toast.makeText(this, getString(R.string.toast_set_time), Toast.LENGTH_SHORT).show()
        } else {
            binding.startBtn.isEnabled = false
            binding.startBtn.text = "${Utils.int2String(startTimeH)}:${Utils.int2String(startTimeM)} 开始播放"
            val delay = ((startMinuteCount - nowMinuteCount) * 60 - now.second).toLong()
            val playTime1 = (playTime * 60).toLong()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            wakeLock.acquire((delay+playTime1+5)*1000L)

            musicWorker.request(
                playTime1,
                delay,
                folderPath.toString(),
                selectMusicName
            )
        }
    }

    private fun endTimer() {
        musicWorker.cancel()
        restart()
    }

    private fun playing() {
//        startMusic(folderPath.toString(), selectMusicName)
        binding.startBtn.isEnabled = true
        binding.startBtn.text = getString(R.string.view_button_end)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun restart() {
//        stopMusic()
        binding.startBtn.isEnabled = true
        binding.startBtn.text = getString(R.string.view_button_start)
        if(wakeLock.isHeld) wakeLock.release()
        Broadcast.destroyLocal(this) { msg -> broadcastReceiveHandler(msg)}
    }

    @SuppressLint("SetTextI18n")
    private fun createFolder(isCheck: Boolean = false) {
        val name = getString(R.string.view_music_path_name)
        val path = Utils.createFolder(name, Environment.DIRECTORY_MUSIC)
        if(path == "") {
            if(isCheck) {
                val isWrite = permission.check(WRITE_EXTERNAL_STORAGE)
                if (isWrite) createFolder()
            } else {
                folderPath = "${Environment.getExternalStorageDirectory()}/${Environment.DIRECTORY_MUSIC}"
            }
        } else {
            binding.musicPath.text = "${getString(R.string.view_music_path)}/${name}"
            folderPath = path
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

    private fun startMusic(path: String, name: String="") {
        val intent = Intent(this, MusicService::class.java)
        intent.putExtra("file_path", path)
        intent.putExtra("music_name", name)
        startForegroundService(intent);
    }

    private fun stopMusic() {
        val intent = Intent(this, MusicService::class.java)
        stopService(intent)
    }
}