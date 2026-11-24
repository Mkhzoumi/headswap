package com.yegorkapomidorka.headswap.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.yegorkapomidorka.headswap.AppLocale
import com.yegorkapomidorka.headswap.AppLog
import com.yegorkapomidorka.headswap.BridgeService
import com.yegorkapomidorka.headswap.BuildConfig
import com.yegorkapomidorka.headswap.CarLifeState
import com.yegorkapomidorka.headswap.MainActivity
import com.yegorkapomidorka.headswap.R
import com.yegorkapomidorka.headswap.link.diag.NetProbe
import com.yegorkapomidorka.headswap.ui.Actions

/**
 * Quick Settings tile (fork): shows the bridge state and starts wireless mode or stops the bridge.
 * Runs in the main process next to BridgeService, so it reads CarLifeState directly; while the shade is open it
 * refreshes once a second, like the service notification's ticker.
 */
class HeadswapTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartListening() {
        refresh()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000)
    }

    override fun onStopListening() {
        handler.removeCallbacks(ticker)
    }

    override fun onClick() {
        val i = inputs()
        val action = TileLogic.action(i)
        AppLog.i("QS-плитка: ${TileLogic.mode(i)} -> $action")
        when (action) {
            TileAction.START_WIRELESS ->
                startBridge(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_WIFI_B), WIRELESS_SECTION)
            TileAction.STOP -> startBridge(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_STOP), null)
            TileAction.OPEN_APP -> openApp(null)
            TileAction.OPEN_WIRELESS_SETTINGS -> openApp(WIRELESS_SECTION)
        }
        handler.postDelayed({ refresh() }, 300)
    }

    /** Same intents as the settings button and the notification's Stop; if Android refuses the start from a tile, open the app. */
    private fun startBridge(intent: Intent, fallbackSection: String?) {
        try {
            if (intent.action == BridgeService.ACTION_STOP) Actions.stopBridge(this) else startForegroundService(intent)
        } catch (e: Exception) {
            AppLog.i("QS-плитка: сервис не стартовал (${e.javaClass.simpleName}), открываю Headswap")
            openApp(fallbackSection)
        }
    }

    private fun openApp(section: String?) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (section != null) intent.putExtra(MainActivity.EXTRA_CMD, "screen_settings").putExtra("section", section)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val loc = AppLocale.wrap(this)
        val i = inputs()
        val mode = TileLogic.mode(i)
        val sub = loc.getString(
            when (mode) {
                TileMode.OFF -> if (i.wirelessAvailable) R.string.tile_off else R.string.tile_off_plain
                TileMode.WAITING -> R.string.tile_waiting
                TileMode.CONNECTED_USB -> R.string.tile_connected_usb
                TileMode.CONNECTED_WIFI -> R.string.tile_connected_wifi
            },
        )
        tile.label = loc.getString(R.string.tile_label)
        tile.subtitle = sub
        tile.state = if (mode == TileMode.OFF) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= 30) tile.stateDescription = sub
        tile.updateTile()
    }

    private fun inputs() = TileInputs(
        sessionRunning = CarLifeState.running,
        wirelessSession = CarLifeState.wirelessSession,
        waitingWireless = CarLifeState.wireless != null || CarLifeState.dongle,
        serviceAlive = BridgeService.alive,
        wirelessAvailable = BuildConfig.WIRELESS_BETA,
        localNetworkGranted = Build.VERSION.SDK_INT < 37 || NetProbe.localNetworkGranted(this) == "granted",
    )

    private companion object {
        /** SettingsSection.WIRELESS.key: MainActivity's "screen_settings" command opens that section. */
        const val WIRELESS_SECTION = "wireless"
    }
}
