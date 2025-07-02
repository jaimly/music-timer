package org.ganquan.musictimer.tools

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class Utils {
    companion object {
        fun getTime(): ZonedDateTime {
            val zoneId = ZoneId.of("Asia/Shanghai")
            val now = ZonedDateTime.now(zoneId)
            return now
        }

        fun int2String(i: Int, len: Int = 2): String {
            var s: String = i.toString()
            if(s.length >= len) return s
            return s.padStart(len, '0')
        }

        fun getFileList(folderPath: String, isChildren: Boolean = false): List<File> {
            val list = mutableListOf<File>()
            val dir = File(folderPath)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    list.add(file)
                }

            } else {
                println("$folderPath 文件夹不存在")
            }
            list.sortBy { it.name }
            return list
        }

        fun createFolder(folderName: String, parentFolderName: String): String {
            val folder = File(
                Environment.getExternalStorageDirectory(),
                "$parentFolderName/$folderName"
            )
            if (folder.exists()) return folder.path
            try {
                if(folder.mkdirs()) return folder.path
                return ""
            } catch (e: Exception) {
                println(e)
                return ""
            }
        }

        fun sharedPrefer(context: Context, key: String = "", value: Any? = null): Any? {
            val sharedPref = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
            if(key == "") return sharedPref.edit { clear() }
            return when(value) {
                null -> sharedPref.edit { remove(key) }
                "MutableList" -> Gson().fromJson(sharedPref.getString(key, "[]"),
                                        object : TypeToken<MutableList<*>>() {}.type)
                String -> sharedPref.getString(key, null)
                Int -> sharedPref.getInt(key, -1)
                is String -> sharedPref.edit(commit = true) { putString(key, value) }
                is Int -> sharedPref.edit(commit = true) { putInt(key, value) }
                is MutableList<*> -> sharedPref.edit(commit = true) {
                                        putString(key, Gson().toJson(value))
                                    }

                else -> null
            }
        }
    }
}