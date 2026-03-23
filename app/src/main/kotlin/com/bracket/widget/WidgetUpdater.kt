package com.bracket.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.bracket.MainActivity
import com.bracket.R
import com.bracket.scoring.Scorer
import com.bracket.storage.Storage

object WidgetUpdater {

    const val ACTION_REFRESH = "com.bracket.WIDGET_REFRESH"
    const val ACTION_TOGGLE  = "com.bracket.WIDGET_TOGGLE_SCORING"
    const val PREFS_NAME       = "bracket_widget_prefs"
    const val PREF_IS_CUSTOM   = "is_custom_scoring"
    const val PREF_LAST_UPDATED = "last_updated"

    // Rank colours: gold, silver, bronze, then neutral
    private val RANK_COLORS = listOf(
        Color.parseColor("#F57F17"),  // #1 amber gold
        Color.parseColor("#9E9E9E"),  // #2 silver
        Color.parseColor("#8D6E63"),  // #3 bronze
        Color.parseColor("#534340"),  // #4
        Color.parseColor("#534340"),  // #5
        Color.parseColor("#534340"),  // #6
    )

    private val SMALL_ROW_IDS = listOf(
        Triple(R.id.row1_rank, R.id.row1_name, R.id.row1_score),
        Triple(R.id.row2_rank, R.id.row2_name, R.id.row2_score),
        Triple(R.id.row3_rank, R.id.row3_name, R.id.row3_score),
    )

    private val LARGE_ROW_IDS = listOf(
        Triple(R.id.row1_rank, R.id.row1_name, R.id.row1_score),
        Triple(R.id.row2_rank, R.id.row2_name, R.id.row2_score),
        Triple(R.id.row3_rank, R.id.row3_name, R.id.row3_score),
        Triple(R.id.row4_rank, R.id.row4_name, R.id.row4_score),
        Triple(R.id.row5_rank, R.id.row5_name, R.id.row5_score),
        Triple(R.id.row6_rank, R.id.row6_name, R.id.row6_score),
    )

    fun updateAll(context: Context) {
        val manager  = AppWidgetManager.getInstance(context)
        val smallIds = manager.getAppWidgetIds(ComponentName(context, BracketWidgetSmall::class.java))
        val largeIds = manager.getAppWidgetIds(ComponentName(context, BracketWidgetLarge::class.java))
        if (smallIds.isNotEmpty()) updateWidgets(context, manager, smallIds, isLarge = false)
        if (largeIds.isNotEmpty()) updateWidgets(context, manager, largeIds, isLarge = true)
    }

    private fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        isLarge: Boolean
    ) {
        val prefs       = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isCustom    = prefs.getBoolean(PREF_IS_CUSTOM, false)
        val lastUpdated = prefs.getString(PREF_LAST_UPDATED, null)

        val structure = Storage.loadCachedTournamentStructure(context)
        val brackets  = Storage.loadAllBrackets(context)
        val scores    = if (structure != null) Scorer.scoreAll(brackets, structure) else emptyList()
        val sorted    = if (isCustom) scores.sortedByDescending { it.totalCustom }
                        else          scores.sortedByDescending { it.totalTraditional }

        val layoutId      = if (isLarge) R.layout.widget_large else R.layout.widget_small
        val providerClass = if (isLarge) BracketWidgetLarge::class.java else BracketWidgetSmall::class.java
        val rowIds        = if (isLarge) LARGE_ROW_IDS else SMALL_ROW_IDS

        for (id in ids) {
            val views = RemoteViews(context.packageName, layoutId)
            bindHeader(context, views, isCustom, providerClass, id)
            bindRows(views, sorted, rowIds, isCustom)
            if (isLarge) bindFooter(views, lastUpdated)
            manager.updateAppWidget(id, views)
        }
    }

    private fun bindHeader(
        context: Context,
        views: RemoteViews,
        isCustom: Boolean,
        providerClass: Class<*>,
        appWidgetId: Int
    ) {
        views.setTextViewText(R.id.widget_mode_label, if (isCustom) "CUSTOM" else "TRAD")

        // Use appWidgetId-based request codes so each widget instance gets
        // distinct PendingIntents — shared codes cause them to overwrite each other.
        val togglePending = PendingIntent.getBroadcast(
            context, appWidgetId * 10 + 0,
            Intent(context, providerClass).apply {
                action = ACTION_TOGGLE
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val refreshPending = PendingIntent.getBroadcast(
            context, appWidgetId * 10 + 1,
            Intent(context, providerClass).apply {
                action = ACTION_REFRESH
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            context, appWidgetId * 10 + 2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Whole widget opens the app; individual buttons override below
        views.setOnClickPendingIntent(R.id.widget_root, openPending)

        // Android 12+: setOnClickResponse works correctly inside widget stacks.
        // Pre-12: fall back to setOnClickPendingIntent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setOnClickResponse(R.id.widget_mode_label, RemoteViews.RemoteResponse.fromPendingIntent(togglePending))
            views.setOnClickResponse(R.id.widget_refresh,    RemoteViews.RemoteResponse.fromPendingIntent(refreshPending))
        } else {
            views.setOnClickPendingIntent(R.id.widget_mode_label, togglePending)
            views.setOnClickPendingIntent(R.id.widget_refresh,    refreshPending)
        }
    }

    private fun bindRows(
        views: RemoteViews,
        scores: List<com.bracket.models.PlayerScore>,
        rowIds: List<Triple<Int, Int, Int>>,
        isCustom: Boolean
    ) {
        rowIds.forEachIndexed { index, (rankId, nameId, scoreId) ->
            val score = scores.getOrNull(index)
            if (score != null) {
                val pts = if (isCustom) score.totalCustom else score.totalTraditional
                views.setTextViewText(rankId,  "#${index + 1}")
                views.setTextViewText(nameId,  score.playerName)
                views.setTextViewText(scoreId, "$pts")
                views.setTextColor(rankId, RANK_COLORS.getOrElse(index) { RANK_COLORS.last() })
                views.setViewVisibility(rankId,  View.VISIBLE)
                views.setViewVisibility(nameId,  View.VISIBLE)
                views.setViewVisibility(scoreId, View.VISIBLE)
            } else {
                views.setViewVisibility(rankId,  View.INVISIBLE)
                views.setViewVisibility(nameId,  View.INVISIBLE)
                views.setViewVisibility(scoreId, View.INVISIBLE)
            }
        }
    }

    private fun bindFooter(views: RemoteViews, lastUpdated: String?) {
        if (lastUpdated != null) {
            views.setTextViewText(R.id.widget_updated, "Updated $lastUpdated")
            views.setViewVisibility(R.id.widget_updated, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_updated, View.GONE)
        }
    }
}
