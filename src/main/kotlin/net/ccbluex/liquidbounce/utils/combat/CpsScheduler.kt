/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2023 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.utils.combat

import net.ccbluex.liquidbounce.config.Configurable
import net.ccbluex.liquidbounce.event.Listenable
import kotlin.math.roundToInt

/**
 * Keeps track of all click scheduler
 */
object ClickTracker {
    var lastClick = 0L
}

/**
 * A CPS scheduler
 *
 * Minecraft is counting every click until it handles all inputs.
 * code:
 * while(this.options.keyAttack.wasPressed()) {
 *     this.doAttack();
 * }
 */
class CpsScheduler : Configurable("CpsScheduler"), Listenable {

    private val cps by intRange("CPS", 6..8, 1..20)

    private var clickTime = -1L
    private var stamina = 100
    private var staminaSwitch = false

    // Stamina (0 is off)
    private val staminaDecrement by int("StaminaDecrement", 0, 0..10)

    companion object {
        const val MINECRAFT_TIME_MS = 50L
    }

    fun isClickOnNextTick(ticks: Int = 1) = clickTime != -1L
        && (System.currentTimeMillis() - ClickTracker.lastClick + (MINECRAFT_TIME_MS * ticks)) >= clickTime

    fun clicks(condition: () -> Boolean): Int {
        var clicks = 0

        if (shouldUpdateClickTime()) {
            performClick()
            return if (condition()) 1 else 0
        }

        while (canClick(condition)) {
            performClick()
            clicks++
        }

        return clicks
    }

    private fun shouldUpdateClickTime() =
        clickTime == -1L || (System.currentTimeMillis() - ClickTracker.lastClick - clickTime) / MINECRAFT_TIME_MS > 1

    private fun canClick(condition: () -> Boolean): Boolean {
        val timeLeft = System.currentTimeMillis() - ClickTracker.lastClick
        val clicks = (timeLeft.toFloat() / clickTime.toFloat())
        return clicks > 0.95 && condition() && stamina > 0
    }

    private fun performClick() {
        if (staminaDecrement > 0) {
            if (stamina > 100) {
                staminaSwitch = true
            } else if (stamina < 0) {
                staminaSwitch = false
            }
            if (staminaSwitch) {
                stamina -= staminaDecrement
            } else {
                stamina += staminaDecrement
            }

            clickTime = 1000L / (cps.random() * (stamina / 100.0)).roundToInt().coerceIn(cps)
        } else {
            clickTime = 1000L / cps.random()
        }

        ClickTracker.lastClick = System.currentTimeMillis()
    }

}
