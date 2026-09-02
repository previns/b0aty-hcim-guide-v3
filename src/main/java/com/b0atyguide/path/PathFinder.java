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

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.runelite.api.CollisionDataFlag;

/**
 * A walkable route between two tiles of the loaded scene.
 *
 * <p>Breadth-first over the client's own collision flags, so the path goes
 * through doorways rather than walls. A straight line to the target is easier
 * and is what most guides draw, but it points confidently through buildings and
 * over rivers, which is worse than drawing nothing.
 *
 * <p>Scene-local coordinates throughout (0..103). The scene is ~10,800 tiles,
 * so a full search is microseconds; it is recomputed only when the player
 * changes tile.
 *
 * <p>Deliberately free of {@code Client}: this is the one piece of the overlay
 * work with real logic in it, and it is worth being able to test on a hand-drawn
 * map.
 */
public final class PathFinder
{
	/** Scene edge length in tiles. */
	public static final int SCENE = 104;

	/**
	 * Cap on tiles visited. A destination walled off from the player would
	 * otherwise flood the whole scene every time the player takes a step.
	 */
	private static final int MAX_VISITED = SCENE * SCENE;

	// Clockwise from north. Diagonals last so a tie prefers a cardinal step,
	// which reads more naturally when drawn.
	private static final int[] DX = {0, 1, 0, -1, 1, 1, -1, -1};
	private static final int[] DY = {1, 0, -1, 0, 1, -1, -1, 1};
	private static final int[] BLOCKED_BY = {
		CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
		CollisionDataFlag.BLOCK_MOVEMENT_EAST,
		CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,
		CollisionDataFlag.BLOCK_MOVEMENT_WEST,
		CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST,
		CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST,
		CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST,
		CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST,
	};

	private PathFinder()
	{
	}

	/**
	 * @param flags scene collision flags, [x][y]
	 * @return tiles from start to destination inclusive, or empty when there is
	 *     no walkable route
	 */
	public static List<Point> find(int[][] flags, int startX, int startY, int destX, int destY)
	{
		if (flags == null
			|| outside(startX, startY) || outside(destX, destY)
			|| flags.length < SCENE)
		{
			return Collections.emptyList();
		}
		if (startX == destX && startY == destY)
		{
			return Collections.singletonList(new Point(startX, startY));
		}

		final int[] cameFrom = new int[SCENE * SCENE];
		java.util.Arrays.fill(cameFrom, -1);
		final int start = index(startX, startY);
		final int goal = index(destX, destY);
		cameFrom[start] = start;

		final Deque<Integer> queue = new ArrayDeque<>();
		queue.add(start);
		int visited = 0;

		while (!queue.isEmpty() && visited++ < MAX_VISITED)
		{
			final int current = queue.poll();
			if (current == goal)
			{
				return reconstruct(cameFrom, start, goal);
			}
			final int x = current % SCENE;
			final int y = current / SCENE;

			for (int dir = 0; dir < DX.length; dir++)
			{
				final int nx = x + DX[dir];
				final int ny = y + DY[dir];
				if (outside(nx, ny) || cameFrom[index(nx, ny)] != -1)
				{
					continue;
				}
				if (!walkable(flags, x, y, nx, ny, dir))
				{
					continue;
				}
				cameFrom[index(nx, ny)] = current;
				queue.add(index(nx, ny));
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Whether a step from one tile to its neighbour is legal.
	 *
	 * <p>A diagonal needs both of its cardinal components clear as well: the
	 * game will not let a player cut the corner of a building, and a path that
	 * assumes otherwise sends them into a wall.
	 */
	private static boolean walkable(int[][] flags, int x, int y, int nx, int ny, int dir)
	{
		if ((flags[x][y] & BLOCKED_BY[dir]) != 0)
		{
			return false;
		}
		if ((flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
		{
			return false;
		}
		if (dir < 4)
		{
			return true;
		}
		// Diagonal: check the two cardinals it is made of.
		final int dx = DX[dir];
		final int dy = DY[dir];
		return passable(flags, x, y, dx, 0) && passable(flags, x, y, 0, dy);
	}

	private static boolean passable(int[][] flags, int x, int y, int dx, int dy)
	{
		final int nx = x + dx;
		final int ny = y + dy;
		if (outside(nx, ny))
		{
			return false;
		}
		final int blocking = dx > 0 ? CollisionDataFlag.BLOCK_MOVEMENT_EAST
			: dx < 0 ? CollisionDataFlag.BLOCK_MOVEMENT_WEST
			: dy > 0 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH
			: CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		return (flags[x][y] & blocking) == 0
			&& (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
	}

	private static List<Point> reconstruct(int[] cameFrom, int start, int goal)
	{
		final List<Point> path = new ArrayList<>();
		int at = goal;
		while (at != start)
		{
			path.add(new Point(at % SCENE, at / SCENE));
			at = cameFrom[at];
		}
		path.add(new Point(start % SCENE, start / SCENE));
		Collections.reverse(path);
		return path;
	}

	private static boolean outside(int x, int y)
	{
		return x < 0 || y < 0 || x >= SCENE || y >= SCENE;
	}

	private static int index(int x, int y)
	{
		return y * SCENE + x;
	}
}
