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
 *
 */

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.repeatable
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.component1
import net.ccbluex.liquidbounce.utils.client.component2
import net.ccbluex.liquidbounce.utils.client.component3
import net.ccbluex.liquidbounce.utils.combat.CpsScheduler
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.combat.attack
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d
import kotlin.math.ceil

/**
 * A super basic and most anti-cheat unfriendly killaura BUT it can teleport.
 */
object ModuleTpAura : Module("TpAura", Category.COMBAT) {

    private val cps by intRange("CPS", 6..10, 1..20)

    object Cooldown : ToggleableConfigurable(this, "Cooldown", true) {

        private val rangeCooldown by floatRange("CooldownRange", 0.9f..1f, 0f..1f)

        var nextCooldown = rangeCooldown.random()
            private set

        val readyToAttack: Boolean
            get() = !this.enabled || player.getAttackCooldownProgress(0.0f) >= cooldown.nextCooldown

        fun newCooldown() {
            nextCooldown = rangeCooldown.random()
        }

    }

    val cooldown = tree(Cooldown)

    private val maximumRange by float("MaximumRange", 50f, 5f..50f)
    private val maximumPackets by int("MaximumPackets", 100, 1..200)
    private val teleportOffset by float("TeleportOffset", 0.5f, 0.1f..5f)
    private val targetTracker = tree(TargetTracker())

    private val cpsScheduler = CpsScheduler()

    val repeatable = repeatable {
        // Finds a target
        targetTracker.validateLock { it.shouldBeAttacked() && it.isAlive && it.distanceTo(mc.player) <= maximumRange }
        val target = targetTracker.lockedOnTarget ?:
            targetTracker.enemies().firstOrNull {
                it.distanceTo(mc.player) <= maximumRange
            } ?: return@repeatable
        targetTracker.lock(target)

        val path = findPath(target, teleportOffset.toDouble())
        if (path.isEmpty()) {
            return@repeatable
        }
        if (path.size > maximumPackets) {
            targetTracker.cleanup()
            return@repeatable
        }

        // Teleport to enemy
        for (pos in path) {
            network.sendPacket(PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, true))
        }

        repeat(cpsScheduler.clicks({ cooldown.readyToAttack }, cps)) {
            target.attack(true)
        }

        // Teleport back from enemy
        for (pos in path.reversed()) {
            network.sendPacket(PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, true))
        }

        wait(10)
    }

    /**
     * Finds a path to the entity in the worst way possible.
     * It just goes in a straight line without checking for obstacles.
     */
    private fun findPath(entity: Entity, offset: Double): List<Vec3d> {
        val positions = mutableListOf<Vec3d>()
        val steps = ceil(entity.distanceTo(mc.player) / offset)

        val (tpX, tpY, tpZ) = entity.pos
        val (x, y, z) = player.pos
        val dX = tpX - x
        val dY = tpY - y
        val dZ = tpZ - z

        var d = 1.0
        while (d <= steps) {
            val nextPosition = Vec3d(x + dX * d / steps, y + dY * d / steps, z + dZ * d / steps)
            ++d
            positions += nextPosition
        }

        return positions
    }


}

