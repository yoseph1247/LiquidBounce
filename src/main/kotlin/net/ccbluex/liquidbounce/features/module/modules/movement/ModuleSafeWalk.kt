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

package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.config.Choice
import net.ccbluex.liquidbounce.config.ChoiceConfigurable
import net.ccbluex.liquidbounce.config.NoneChoice
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerSafeWalkEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.entity.isCloseToEdge
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput

/**
 * SafeWalk module
 *
 * Prevents you from falling down as if you were sneaking.
 */
object ModuleSafeWalk : Module("SafeWalk", Category.MOVEMENT) {

    @Suppress("UnusedPrivateProperty")
    private val modes = choices("Mode", {
        it.choices[1] // Safe mode
    }) {
        arrayOf(NoneChoice(it), Safe(it), Simulate(it), OnEdge(it))
    }

    class Safe(override val parent: ChoiceConfigurable) : Choice("Safe") {

        val safeWalkHandler = handler<PlayerSafeWalkEvent> { event ->
            event.isSafeWalk = true
        }

    }

    class Simulate(override val parent: ChoiceConfigurable) : Choice("Simulate") {

        private val predict by int("Ticks", 5, 0..20)

        /**
         * The input handler tracks the movement of the player and calculates the predicted future position.
         */
        private val inputHandler = handler<MovementInputEvent> { event ->
            if (player.isOnGround && !player.isSneaking) {
                val simulatedPlayer = SimulatedPlayer.fromClientPlayer(
                    SimulatedPlayer.SimulatedPlayerInput(
                        event.directionalInput,
                        event.jumping,
                        player.isSprinting,
                        player.isSneaking
                    ))

                repeat(predict) {
                    simulatedPlayer.tick()
                }

                if (simulatedPlayer.fallDistance > 0.0) {
                    event.directionalInput = DirectionalInput.NONE
                    return@handler
                }
            }
        }

    }

    class OnEdge(override val parent: ChoiceConfigurable) : Choice("OnEdge") {

        private val edgeDistance by float("EdgeDistance", 0.01f, 0.01f..0.5f)

        /**
         * The input handler tracks the movement of the player and calculates the predicted future position.
         */
        private val inputHandler = handler<MovementInputEvent> { event ->
            val shouldBeActive = player.isOnGround && !player.isSneaking

            if (shouldBeActive && player.isCloseToEdge(event.directionalInput, edgeDistance.toDouble())) {
                event.directionalInput = DirectionalInput.NONE
            }
        }

    }

}
