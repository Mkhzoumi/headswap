package com.yegorkapomidorka.headswap.tile

/** What the Quick Settings tile shows. */
enum class TileMode { OFF, WAITING, CONNECTED_USB, CONNECTED_WIFI }

/** What a tap on the tile does. */
enum class TileAction { START_WIRELESS, STOP, OPEN_APP, OPEN_WIRELESS_SETTINGS }

/** Bridge state the tile reads; the service fills it from CarLifeState / BridgeService, tests build it by hand. */
data class TileInputs(
    /** A CarLife session is up (CarLifeState.running), in Android Auto or desktop mode. */
    val sessionRunning: Boolean,
    /** That session goes over Wi-Fi (CarLifeState.wirelessSession). */
    val wirelessSession: Boolean,
    /** The bridge waits for the car over Wi-Fi or for the dongle (CarLifeState.wireless / dongle). */
    val waitingWireless: Boolean,
    /** BridgeService is alive. */
    val serviceAlive: Boolean,
    /** Wireless mode is built in (BuildConfig.WIRELESS_BETA). */
    val wirelessAvailable: Boolean,
    /** Android 17+: ACCESS_LOCAL_NETWORK granted; without it the wireless button asks first, which a tile can't. */
    val localNetworkGranted: Boolean,
)

/** Pure tile rules, unit-tested in TileLogicTest. */
object TileLogic {
    fun mode(i: TileInputs): TileMode = when {
        i.sessionRunning -> if (i.wirelessSession) TileMode.CONNECTED_WIFI else TileMode.CONNECTED_USB
        i.serviceAlive && i.waitingWireless -> TileMode.WAITING
        else -> TileMode.OFF
    }

    fun action(i: TileInputs): TileAction = when (mode(i)) {
        TileMode.CONNECTED_USB, TileMode.CONNECTED_WIFI, TileMode.WAITING -> TileAction.STOP
        TileMode.OFF -> when {
            !i.wirelessAvailable -> TileAction.OPEN_APP
            !i.localNetworkGranted -> TileAction.OPEN_WIRELESS_SETTINGS
            else -> TileAction.START_WIRELESS
        }
    }
}
