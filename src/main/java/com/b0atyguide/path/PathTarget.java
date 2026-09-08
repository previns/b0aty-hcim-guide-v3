/*
 * Copyright (c) 2026, Previn <https://github.com/previns>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.b0atyguide.path;

import net.runelite.api.CollisionDataFlag;

/**
 * The scene tiles occupied by a destination, and how the player reaches it.
 *
 * <p>A game object's world location is its centre, not an interaction tile.
 * Reducing a 3x3 object to that point makes every walkable tile at least two
 * tiles away. The former one-tile arrival test consequently rejected the
 * correct route through a gate and returned the outside-wall fallback. Keep
 * the actual scene bounds supplied by the client and search for an accessible
 * edge of them. Never expand a radius or flood all touching blocked scenery:
 * either would include the enclosure wall we are meant to respect.
 */
public final class PathTarget
{
	private final int minX;
	private final int minY;
	private final int maxX;
	private final int maxY;
	private final boolean interaction;
	private final boolean wall;

	private PathTarget(int minX, int minY, int maxX, int maxY,
		boolean interaction, boolean wall)
	{
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.interaction = interaction;
		this.wall = wall;
	}

	/** A travel coordinate which must itself be reached. */
	public static PathTarget tile(int x, int y)
	{
		return new PathTarget(x, y, x, y, false, false);
	}

	/** Inclusive, already rotated scene bounds of a loaded object or NPC. */
	public static PathTarget object(int minX, int minY, int maxX, int maxY)
	{
		return new PathTarget(minX, minY, maxX, maxY, true, false);
	}

	/**
	 * A wall is itself the thing to click, so its own blocked edge is allowed
	 * for interaction. This exception never applies to scenery behind that wall.
	 */
	public static PathTarget wall(int x, int y)
	{
		return new PathTarget(x, y, x, y, true, true);
	}

	/** Legacy point targets have no size evidence; only their own tile is known. */
	static PathTarget location(int[][] flags, int x, int y)
	{
		return flags != null && x >= 0 && x < flags.length && flags[x] != null
			&& y >= 0 && y < flags[x].length
			&& (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0
			? object(x, y, x, y) : tile(x, y);
	}

	boolean isValid()
	{
		return minX >= 0 && minY >= 0 && maxX >= minX && maxY >= minY
			&& maxX < PathFinder.SCENE && maxY < PathFinder.SCENE;
	}

	/**
	 * A legal place to stand and interact, without crossing a wall at the final
	 * edge. Only the object's occupancy is ignored; walls on both sides of the
	 * interaction edge are still checked. Diagonal corner-touching is not access.
	 */
	boolean reached(int[][] flags, int x, int y)
	{
		if (!interaction)
		{
			return x == minX && y == minY;
		}
		if (x < 0 || y < 0 || x >= PathFinder.SCENE || y >= PathFinder.SCENE
			|| (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
		{
			return false;
		}
		if (x >= minX && x <= maxX && y >= minY && y <= maxY)
		{
			// Floor decorations and NPCs can occupy otherwise walkable ground.
			return true;
		}
		int edge;
		int reverse;
		int targetX = x;
		int targetY = y;
		if (y >= minY && y <= maxY && x == minX - 1)
		{
			targetX = minX;
			edge = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
			reverse = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		else if (y >= minY && y <= maxY && x == maxX + 1)
		{
			targetX = maxX;
			edge = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
			reverse = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		}
		else if (x >= minX && x <= maxX && y == minY - 1)
		{
			targetY = minY;
			edge = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
			reverse = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		}
		else if (x >= minX && x <= maxX && y == maxY + 1)
		{
			targetY = maxY;
			edge = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
			reverse = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
		}
		else
		{
			return false;
		}
		return wall || ((flags[x][y] & edge) == 0
			&& (flags[targetX][targetY] & reverse) == 0);
	}

	/** Squared distance to the occupied rectangle, for an honest partial path. */
	int gap(int x, int y)
	{
		final int dx = Math.max(0, Math.max(minX - x, x - maxX));
		final int dy = Math.max(0, Math.max(minY - y, y - maxY));
		return dx * dx + dy * dy;
	}

	/** Size evidence visible in diagnostics, so a live report identifies the goal. */
	public String describe()
	{
		return (maxX - minX + 1) + "x" + (maxY - minY + 1)
			+ (interaction ? (wall ? " wall" : " interaction") : " tile");
	}
}
