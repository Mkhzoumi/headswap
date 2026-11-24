package com.yegorkapomidorka.headswap.tile

import org.junit.Assert.assertEquals
import org.junit.Test

class TileLogicTest {
    private val idle = TileInputs(
        sessionRunning = false, wirelessSession = false, waitingWireless = false,
        serviceAlive = false, wirelessAvailable = true, localNetworkGranted = true,
    )

    @Test
    fun offStartsWireless() {
        assertEquals(TileMode.OFF, TileLogic.mode(idle))
        assertEquals(TileAction.START_WIRELESS, TileLogic.action(idle))
    }

    @Test
    fun offWithoutLocalNetworkOpensWirelessSettings() {
        val i = idle.copy(localNetworkGranted = false)
        assertEquals(TileMode.OFF, TileLogic.mode(i))
        assertEquals(TileAction.OPEN_WIRELESS_SETTINGS, TileLogic.action(i))
    }

    @Test
    fun offWithoutWirelessBuildOpensApp() {
        val i = idle.copy(wirelessAvailable = false, localNetworkGranted = false)
        assertEquals(TileAction.OPEN_APP, TileLogic.action(i))
    }

    @Test
    fun waitingForCarStops() {
        val i = idle.copy(serviceAlive = true, waitingWireless = true)
        assertEquals(TileMode.WAITING, TileLogic.mode(i))
        assertEquals(TileAction.STOP, TileLogic.action(i))
    }

    @Test
    fun staleWirelessTextWithoutServiceIsOff() {
        assertEquals(TileMode.OFF, TileLogic.mode(idle.copy(waitingWireless = true)))
    }

    @Test
    fun aliveServiceWithoutSessionOrWaitIsOff() {
        assertEquals(TileMode.OFF, TileLogic.mode(idle.copy(serviceAlive = true)))
    }

    @Test
    fun usbSessionStops() {
        val i = idle.copy(sessionRunning = true, serviceAlive = true)
        assertEquals(TileMode.CONNECTED_USB, TileLogic.mode(i))
        assertEquals(TileAction.STOP, TileLogic.action(i))
    }

    @Test
    fun wifiSessionWinsOverWaiting() {
        val i = idle.copy(sessionRunning = true, wirelessSession = true, waitingWireless = true, serviceAlive = true)
        assertEquals(TileMode.CONNECTED_WIFI, TileLogic.mode(i))
        assertEquals(TileAction.STOP, TileLogic.action(i))
    }
}
