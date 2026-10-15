package com.xelvor.ai

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class XelvorDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "تم تفعيل إدارة الجهاز لـ XELVOR. الاستخدام الحالي: قفل الشاشة فقط.", Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "تم إلغاء إدارة الجهاز لـ XELVOR.", Toast.LENGTH_SHORT).show()
    }
}
