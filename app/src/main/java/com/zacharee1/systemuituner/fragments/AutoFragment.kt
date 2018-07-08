package com.zacharee1.systemuituner.fragments

import android.Manifest
import android.content.Intent
import android.preference.Preference
import android.preference.SwitchPreference
import android.support.constraint.ConstraintLayout
import android.view.LayoutInflater
import com.zacharee1.systemuituner.R
import com.zacharee1.systemuituner.activites.instructions.SetupActivity
import com.zacharee1.systemuituner.util.SettingsUtils
import com.zacharee1.systemuituner.util.Utils
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.regex.Pattern

class AutoFragment : AnimFragment() {
    private val prefs = TreeMap<String, Preference>()

    private lateinit var observable: Disposable

    override fun onSetTitle() = resources.getString(R.string.auto_detect)

    override fun onAnimationFinishedEnter(enter: Boolean) {
        val content = activity.findViewById<ConstraintLayout>(R.id.content_main)

        if (enter) {
            LayoutInflater.from(activity).inflate(R.layout.indet_circle_prog, content, true)

            addPreferencesFromResource(R.xml.pref_auto)

            val hasUsage = SettingsUtils.hasSpecificPerm(context, Manifest.permission.PACKAGE_USAGE_STATS)
            val hasDump = SettingsUtils.hasSpecificPerm(context, Manifest.permission.DUMP)

            if (hasDump && hasUsage) {
                observable = Observable.fromCallable { Utils.runCommand("dumpsys activity service com.android.systemui/.SystemUIService") }
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe { dump ->
                            dump?.let {
                                val index = dump.indexOf("icon slots")
                                if (index != -1) {
                                    val icons = dump.substring(index)
                                    val ico = ArrayList(icons.split("\n"))
                                    ico.removeAt(0)
                                    for (slot in ico) {
                                        if (slot.startsWith("         ") || slot.startsWith("        ")) {
                                            val p = Pattern.compile("\\((.*?)\\)")
                                            val m = p.matcher(slot)

                                            while (!m.hitEnd()) {
                                                if (activity == null) return@subscribe
                                                if (m.find()) {
                                                    val result = m.group().replace("(", "").replace(")", "")

                                                    val preference = SwitchPreference(context)
                                                    preference.title = result
                                                    preference.key = result
                                                    preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, o ->
                                                        SettingsUtils.changeBlacklist(preference.key, java.lang.Boolean.valueOf(o.toString()), context)
                                                        true
                                                    }

                                                    prefs[preference.key] = preference
                                                    break
                                                }
                                            }
                                        } else
                                            break
                                    }
                                }

                                val p = Pattern.compile("slot=(.+?)\\s")
                                val m = p.matcher(dump)
                                var find = ""

                                while (!m.hitEnd()) if (m.find()) find = find + m.group() + "\n"

                                val slots = ArrayList(find.split("\n"))
                                for (slot in slots) {
                                    val slotNew = slot.replace("slot=", "").replace(" ", "")

                                    val preference = SwitchPreference(context)
                                    preference.title = slotNew
                                    preference.key = slotNew
                                    preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, o ->
                                        SettingsUtils.changeBlacklist(preference.key, o.toString().toBoolean(), context)
                                        true
                                    }

                                    if (!preference.key.isBlank() && !preference.title.toString().isBlank()) {
                                        prefs[preference.key] = preference
                                    }
                                }

                                if (prefs.values.isNotEmpty()) {
                                    for (preference in prefs.values) {
                                        preferenceScreen.addPreference(preference)
                                    }
                                } else {
                                    val notSupported = Preference(activity)
                                    notSupported.setSummary(R.string.feature_not_supported)
                                    notSupported.isSelectable = false
                                    preferenceScreen.addPreference(notSupported)
                                }

                                activity.runOnUiThread {
                                    SettingsUtils.shouldSetSwitchChecked(this)
                                }
                            }

                            activity.runOnUiThread {
                                content.removeView(content.findViewById(R.id.progress))
                            }
                        }
            } else {
                val intent = Intent(context, SetupActivity::class.java)
                val perms = ArrayList<String>()
                if (!hasUsage) perms.add(Manifest.permission.PACKAGE_USAGE_STATS)
                if (!hasDump) perms.add(Manifest.permission.DUMP)

                intent.putExtra("permission_needed", perms.toTypedArray())
                startActivity(intent)

                activity?.finish()
            }
        }
    }

    override fun onAnimationCreated(enter: Boolean) {
        val content = activity.findViewById<ConstraintLayout>(R.id.content_main)

        if (!enter) {
            Thread {
                try {
                    observable.dispose()
                } catch (e: Exception) {}
            }.start()

            content.removeView(content.findViewById(R.id.progress))
        }
    }
}