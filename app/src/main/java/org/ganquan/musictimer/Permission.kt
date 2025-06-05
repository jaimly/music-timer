package org.ganquan.musictimer

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.Manifest.permission.WAKE_LOCK
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val REQUEST_CODE_AUDIO = 1
private const val REQUEST_CODE_WRITE = 2
private const val REQUEST_CODE_WAKE_LOCK = 3

class Permission {
    private val activity: AppCompatActivity
    private var code: Int = 1
    private var types = ArrayList<String>()
    private lateinit var toastMsg: String

    constructor(activity1: AppCompatActivity) {
        activity = activity1
    }

    fun check(type: String): Boolean {
        val results = ArrayList<Int>()
        results.add(ContextCompat.checkSelfPermission(activity, type))
        types.add(type)

        when (type) {
            READ_MEDIA_AUDIO -> {
                toastMsg = activity.getString(R.string.toast_permission_audio)
                code = REQUEST_CODE_AUDIO
                results.add(ContextCompat.checkSelfPermission(activity, READ_EXTERNAL_STORAGE))
                types.add(READ_EXTERNAL_STORAGE)
            }
            WRITE_EXTERNAL_STORAGE -> {
                toastMsg = activity.getString(R.string.toast_permission_write)
                code = REQUEST_CODE_WRITE
            }
            WAKE_LOCK -> {
                toastMsg = activity.getString(R.string.toast_permission_wake_lock)
                code = REQUEST_CODE_WAKE_LOCK
            }
        }

        val fails: List<Int> = results.filter{ it == PackageManager.PERMISSION_GRANTED }
        if (fails.isNotEmpty()) return true
        ActivityCompat.requestPermissions(activity, types.toTypedArray(), code)
        return false
    }

    fun result(
        requestCode: Int,
        grantResults: IntArray
    ): String {
        if (requestCode != code) return ""
        if (grantResults.isEmpty()) return ""
        if (types.size == 1 && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            return types[0]
        } else if ((grantResults.copyOfRange(0, 2).contains(PackageManager.PERMISSION_GRANTED))) {
            return types[0]
        } else {
            showPermissionSettingsDialog()
            return ""
        }
        types = ArrayList()
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(activity)
            .setMessage(toastMsg)
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", activity.packageName, null)
                intent.data = uri
                activity.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}