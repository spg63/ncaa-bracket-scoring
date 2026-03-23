package com.bracket.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.bracket.results.ResultsFetcher
import com.bracket.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Shared logic for both widget sizes. Each subclass is registered separately
 * in the manifest so Android treats them as distinct widget types, letting
 * users install the small and large versions independently.
 */
abstract class BaseBracketWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            WidgetUpdater.ACTION_REFRESH -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fetched = ResultsFetcher.fetchTournamentStructure()
                        if (fetched != null) {
                            Storage.saveTournamentStructure(context, fetched)
                            val time = LocalTime.now()
                                .format(DateTimeFormatter.ofPattern("h:mm a"))
                            context.getSharedPreferences(WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putString(WidgetUpdater.PREF_LAST_UPDATED, time)
                                .apply()
                        }
                    } finally {
                        WidgetUpdater.updateAll(context)
                        pending.finish()
                    }
                }
            }
            WidgetUpdater.ACTION_TOGGLE -> {
                val prefs = context.getSharedPreferences(WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
                val wasCustom = prefs.getBoolean(WidgetUpdater.PREF_IS_CUSTOM, false)
                prefs.edit().putBoolean(WidgetUpdater.PREF_IS_CUSTOM, !wasCustom).apply()
                WidgetUpdater.updateAll(context)
            }
        }
    }
}

class BracketWidgetSmall : BaseBracketWidget()
class BracketWidgetLarge : BaseBracketWidget()
