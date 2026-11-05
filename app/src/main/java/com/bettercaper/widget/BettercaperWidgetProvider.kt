package com.bettercaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bettercaper.R
import com.bettercaper.service.BettercapBackgroundService

class BettercaperWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val wifiIntent = Intent(context, BettercapBackgroundService::class.java).apply {
                action = "ACTION_WIFI_RECON"
            }
            val wifiPendingIntent = PendingIntent.getService(
                context, 0, wifiIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_wifi_recon, wifiPendingIntent)

            val bleIntent = Intent(context, BettercapBackgroundService::class.java).apply {
                action = "ACTION_BLE_RECON"
            }
            val blePendingIntent = PendingIntent.getService(
                context, 1, bleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_ble_recon, blePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
