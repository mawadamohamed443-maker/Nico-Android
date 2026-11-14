package com.nico.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NicoBrain(private val c: Context) {
    private val prefs = c.getSharedPreferences("nico", Context.MODE_PRIVATE)
    private val h = Handler(Looper.getMainLooper())
    private var name: String
        get() = prefs.getString("name", "") ?: ""
        set(v) { prefs.edit().putString("name", v).apply() }

    private val aliases = mapOf("واتساب" to "whatsapp", "يوتيوب" to "youtube", "فيسبوك" to "facebook", "انستجرام" to "instagram",
        "تيك توك" to "tiktok", "تيليجرام" to "telegram", "كروم" to "chrome", "جيميل" to "gmail", "الصور" to "photos",
        "الكاميرا" to "camera", "كاميرا" to "camera", "الخرائط" to "maps", "سبوتيفاي" to "spotify", "سناب" to "snapchat",
        "ماسنجر" to "messenger", "الاعدادات" to "settings", "الحاسبه" to "calculator", "الساعه" to "clock", "المتجر" to "play store")

    private val jokes = listOf("مرة واحد غبي اشترى ساعة ضد الماء، غطسها في البحر لقاها اتبلت!",
        "مرة سمكة قالت لصاحبتها: انتي ليه قلقانة؟ قالت: أصل الدنيا بتجري ورا الشبكة!")
    private val sea = listOf("البحر ده ساكت دلوقتي، بس يوم ما يفيض مفيش حاجة بتقف قدامه.",
        "كل حاجة بتستخبى، بس البحر بيفضل فاكر.")

    private fun norm(s: String): String {
        var t = s.lowercase().replace(Regex("[\\u064B-\\u0652\\u0640]"), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
        for (i in 0..9) t = t.replace('٠' + i, '0' + i)
        return t.replace(Regex("[؟?!.,،]"), " ").replace(Regex("\\s+"), " ").trim()
    }
    private fun has(q: String, vararg w: String) = w.any { q.contains(it) }
    private fun start(i: Intent) = c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun acc() = NicoAccessibilityService.inst
    private fun granted(p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun later(ms: Long, f: () -> Unit) { h.postDelayed(f, ms) }

    private fun needAcc(): String? =
        if (acc() == null) "محتاج تفعّلي \"التحكم الكامل\" من الزر اللي فوق، وبعدها أنفذ الأمر ده." else null

    private fun toggleSetting(intent: Intent, on: Boolean?, label: String): String {
        start(intent)
        if (acc() == null) return "فتحت $label، فعّلي التحكم الكامل علشان أضغط التبديل بنفسي."
        later(1600) { acc()?.clickFirstSwitch(on) }
        return "تمام، بظبط $label."
    }

    private fun torch(on: Boolean): String = try {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cm.setTorchMode(id, on); if (on) "شغلت الكشاف." else "طفيت الكشاف."
    } catch (e: Exception) { "مقدرتش أتحكم في الكشاف." }

    private fun findContact(n: String): String? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
            while (it.moveToNext()) if (norm(it.getString(0) ?: "").contains(n)) return it.getString(1)
        }
        return null
    }

    private fun openApp(n: String): Boolean {
        val pm = c.packageManager
        val target = aliases[n] ?: n
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val m = list.firstOrNull { val l = norm(it.loadLabel(pm).toString()); l.contains(target) || l.contains(n) } ?: return false
        pm.getLaunchIntentForPackage(m.activityInfo.packageName)?.let { start(it); return true }
        return false
    }

    fun handle(raw: String): String {
        val q = norm(raw)
        if (q.isEmpty()) return ""
        Regex("(?:اسمي|ناديني)\\s+(\\S+)").find(q)?.let { name = it.groupValues[1]; return "تشرفت يا $name، هفتكر اسمك." }
        if (has(q, "انا مين", "تعرفني")) return if (name.isEmpty()) "لسه معرفش اسمك. قولي: اسمي وبعدها اسمك." else "انتي $name طبعا."
        if (has(q, "انت مين", "مين انت", "عرفني بنفسك")) return "أنا نيكو، مساعد مريانو الذكي. شغال على الموبايل من غير نت وبتحكم فيه بأمرك."
        if (has(q, "مرحبا", "اهلا", "هاي", "ازيك", "عامله ايه", "سلام عليكم") || q == "نيكو") return "أهلا يا ${name.ifEmpty { "قمر" }}! أنا سامعك."
        if (has(q, "شكرا", "تسلم")) return "العفو، ده واجبي."
        if (has(q, "تعمل ايه", "مساعده", "اوامر")) return "بتحكم في الواي فاي والبلوتوث والكشاف والصوت والسطوع، وبفتح التطبيقات، وبتصل وبظبط المنبه والمؤقت، وبضغط وبرجع وبسكرول وبكتب بدالك، وبحسب وبحكي نكت."

        if (has(q, "الساعه كام", "الوقت")) return "الساعة " + SimpleDateFormat("h:mm a", Locale("ar", "EG")).format(Date())
        if (has(q, "النهارده", "التاريخ")) return "النهارده " + SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar", "EG")).format(Date())
        if (has(q, "بطاريه", "الشحن")) {
            val p = (c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return "البطارية $p%."
        }

        if (has(q, "الفلاش", "الكشاف")) return torch(!has(q, "اطفي", "اقفل", "طفي"))
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (has(q, "ارفع الصوت", "علي الصوت", "زود الصوت")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return "رفعت الصوت." }
        if (has(q, "وطي الصوت", "اخفض الصوت", "قلل الصوت")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return "وطيت الصوت." }
        if (has(q, "اكتم الصوت", "كتم")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); return "كتمت الصوت." }
        if (has(q, "السطوع", "الاضاءه")) {
            if (!Settings.System.canWrite(c)) { start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}"))); return "اسمحي لي بتعديل الإعدادات وبعدها كرري الأمر." }
            val cr = c.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 125)
            val up = has(q, "زود", "ارفع", "علي")
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (cur + if (up) 50 else -50).coerceIn(10, 255))
            return if (up) "زودت السطوع." else "قللت السطوع."
        }

        val on: Boolean? = when { has(q, "اقفل", "طفي", "اطفي", "افصل") -> false; has(q, "شغل", "افتح", "وصل") -> true; else -> null }
        if (has(q, "واي فاي", "وايفاي", "wifi"))
            return toggleSetting(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS), on, "الواي فاي")
        if (has(q, "بلوتوث")) return toggleSetting(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), on, "البلوتوث")
        if (has(q, "وضع الطيران", "الطيران")) return toggleSetting(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), on, "وضع الطيران")

        val g = acc()
        val global = when {
            has(q, "ارجع", "رجوع") -> AccessibilityService.GLOBAL_ACTION_BACK
            has(q, "الشاشه الرئيسيه", "روح للرئيسيه", "هوم") -> AccessibilityService.GLOBAL_ACTION_HOME
            has(q, "التطبيقات الاخيره", "الاخيره") -> AccessibilityService.GLOBAL_ACTION_RECENTS
            has(q, "الاشعارات") -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            has(q, "الاعدادات السريعه") -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            has(q, "اقفل الشاشه", "قفل الشاشه") -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            has(q, "سكرين شوت", "لقطه شاشه", "صور الشاشه") -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            else -> null
        }
        if (global != null) { needAcc()?.let { return it }; g!!.global(global); return "تمام." }
        if (has(q, "اسكرول", "انزل", "اطلع")) {
            needAcc()?.let { return it }
            return if (g!!.scroll(!has(q, "اطلع"))) "تمام." else "ملقتش حاجة أسكرولها."
        }
        Regex("^(?:اضغط علي|دوس علي|اضغطي علي)\\s+(.+)").find(q)?.let {
            needAcc()?.let { m -> return m }
            return if (g!!.clickText(it.groupValues[1])) "ضغطت على ${it.groupValues[1]}." else "ملقتش ${it.groupValues[1]} على الشاشة."
        }
        Regex("^(?:اكتب)\\s+(.+)").find(raw.trim().replace(Regex("^\\S*اكتب\\S*"), "اكتب"))?.let {
            needAcc()?.let { m -> return m }
            return if (g!!.typeText(it.groupValues[1])) "كتبت." else "مفيش خانة كتابة مفتوحة."
        }

        Regex("(?:مؤقت|تايمر|نبهني بعد|فكرني بعد)\\s*(\\d+)\\s*(ثانيه|ثواني|دقيقه|دقايق|دقائق|ساعه)?").find(q)?.let {
            val n = it.groupValues[1].toInt()
            val u = it.groupValues[2]
            val sec = when { u.startsWith("ثان") -> n; u.startsWith("ساع") -> n * 3600; else -> n * 60 }
            start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            return "ظبطت المؤقت."
        }
        Regex("(?:منبه|صحيني|نبهني)\\s*(?:الساعه)?\\s*(\\d{1,2})(?:\\s*(?:و|:)?\\s*(\\d{1,2}))?").find(q)?.let {
            var hr = it.groupValues[1].toInt(); val mi = it.groupValues[2].ifEmpty { "0" }.toInt()
            if (has(q, "مساء", "بليل", "العصر") && hr < 12) hr += 12
            start(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hr).putExtra(AlarmClock.EXTRA_MINUTES, mi)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "نيكو").putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            return "ظبطت المنبه على $hr:${mi.toString().padStart(2, '0')}."
        }

        Regex("(?:اتصل ب|اتصل|كلم|رن علي)\\s*(.+)").find(q)?.let {
            val who = it.groupValues[1].trim()
            val digits = who.replace(" ", "")
            val num = if (digits.matches(Regex("\\+?\\d{3,}"))) digits else findContact(who) ?: return "ملقتش رقم لـ $who. اسمحي بالوصول لجهات الاتصال من زر الأذونات."
            start(Intent(if (granted(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num")))
            return "بتصل بـ $who."
        }

        Regex("(?:افتح|شغل|روح ل|روح الي)\\s*(?:تطبيق)?\\s*(.+)").find(q)?.let {
            val n = it.groupValues[1].trim()
            return if (openApp(n)) "بفتح $n." else "ملقتش تطبيق اسمه $n."
        }

        Regex("(\\d+(?:\\.\\d+)?)\\s*(\\+|زائد|زايد|-|ناقص|\\*|x|ضرب|/|علي|قسمه)\\s*(\\d+(?:\\.\\d+)?)").find(q)?.let {
            val a = it.groupValues[1].toDouble(); val b = it.groupValues[3].toDouble()
            val r = when (it.groupValues[2]) { "+", "زائد", "زايد" -> a + b; "-", "ناقص" -> a - b; "*", "x", "ضرب" -> a * b; else -> if (b == 0.0) return "مينفعش القسمة على صفر." else a / b }
            return "الناتج " + if (r % 1.0 == 0.0) r.toLong().toString() else "%.4f".format(r).trimEnd('0')
        }

        Regex("(?:سجل|احفظ)\\s*(?:ملاحظه)?\\s*(.+)").find(q)?.let {
            if (has(q, "ملاحظه", "سجل")) { prefs.edit().putString("notes", (prefs.getString("notes", "") ?: "") + it.groupValues[1] + "\n").apply(); return "سجلت." }
        }
        if (has(q, "ملاحظاتي", "الملاحظات")) return (prefs.getString("notes", "") ?: "").ifEmpty { "مفيش ملاحظات." }
        if (has(q, "نكته", "اضحكني")) return jokes.random()
        if (has(q, "البحر", "يفيض")) return sea.random()
        return "معرفتش قصدك. قولي \"تعمل ايه\" وأقولك أنا أقدر أعمل إيه."
    }
}
