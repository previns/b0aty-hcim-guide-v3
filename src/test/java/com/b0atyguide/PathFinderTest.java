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
package com.b0atyguide;

import com.b0atyguide.path.PathFinder;
import java.awt.Point;
import java.util.List;
import net.runelite.api.CollisionDataFlag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pathfinding on hand-drawn maps.
 *
 * <p>This is the only overlay code with real logic in it, and the failure it
 * exists to avoid -- a confident line through a wall -- is invisible in a unit
 * test of the drawing. So the routing is tested here instead.
 */
public class PathFinderTest
{
	private static int[][] openField()
	{
		return new int[PathFinder.SCENE][PathFinder.SCENE];
	}

	/** A solid north-south wall at x, spanning the scene. */
	private static void wall(int[][] flags, int x)
	{
		for (int y = 0; y < PathFinder.SCENE; y++)
		{
			flags[x][y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		}
	}

	@Test
	public void anOpenFieldGivesADirectPath()
	{
		final List<Point> path = PathFinder.find(openField(), 10, 10, 14, 14);
		assertFalse(path.isEmpty());
		assertEquals(new Point(10, 10), path.get(0));
		assertEquals(new Point(14, 14), path.get(path.size() - 1));
		// Four diagonal steps, so five tiles including the start.
		assertEquals(5, path.size());
	}

	@Test
	public void standingOnTheTargetIsAPathOfOne()
	{
		final List<Point> path = PathFinder.find(openField(), 10, 10, 10, 10);
		assertEquals(1, path.size());
	}

	@Test
	public void aWallIsRoutedAround()
	{
		final int[][] flags = openField();
		wall(flags, 12);
		// Leave one gap, well away from the straight line between the two.
		flags[12][40] = 0;

		final List<Point> path = PathFinder.find(flags, 10, 10, 20, 10);
		assertFalse("a gap exists, so a route exists", path.isEmpty());
		for (Point p : path)
		{
			if (p.x == 12)
			{
				assertEquals("the wall may only be crossed at the gap", 40, p.y);
			}
		}
	}

	@Test
	public void aSealedWallGivesNoPath()
	{
		final int[][] flags = openField();
		wall(flags, 12);
		assertTrue("no gap means no route",
			PathFinder.find(flags, 10, 10, 20, 10).isEmpty());
	}

	@Test
	public void aBlockedDestinationGivesNoPath()
	{
		final int[][] flags = openField();
		flags[14][14] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		assertTrue(PathFinder.find(flags, 10, 10, 14, 14).isEmpty());
	}

	@Test
	public void aDiagonalDoesNotCutACorner()
	{
		// The game will not let a player round the corner of a building
		// diagonally. A path that assumes it can walks them into the wall.
		final int[][] flags = openField();
		flags[11][10] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		flags[10][11] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

		final List<Point> path = PathFinder.find(flags, 10, 10, 11, 11);
		// Reachable only the long way around, so never in a single step.
		assertTrue("must not cut the corner", path.isEmpty() || path.size() > 2);
	}

	@Test
	public void directionalWallsAreRespected()
	{
		// A fence blocks movement north out of a tile without blocking the tile.
		final int[][] flags = openField();
		for (int x = 0; x < PathFinder.SCENE; x++)
		{
			flags[x][12] = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
		}
		assertTrue("a fence with no gate cannot be crossed",
			PathFinder.find(flags, 10, 10, 10, 20).isEmpty());
	}

	@Test
	public void coordinatesOutsideTheSceneAreRejected()
	{
		final int[][] flags = openField();
		assertTrue(PathFinder.find(flags, -1, 10, 14, 14).isEmpty());
		assertTrue(PathFinder.find(flags, 10, 10, PathFinder.SCENE, 14).isEmpty());
	}

	@Test
	public void aNullCollisionMapIsNotAnError()
	{
		assertTrue(PathFinder.find(null, 10, 10, 14, 14).isEmpty());
	}

	@Test
	public void everyStepIsToAnAdjacentTile()
	{
		final int[][] flags = openField();
		wall(flags, 30);
		flags[30][60] = 0;
		final List<Point> path = PathFinder.find(flags, 10, 10, 50, 50);
		assertFalse(path.isEmpty());
		for (int i = 1; i < path.size(); i++)
		{
			final int dx = Math.abs(path.get(i).x - path.get(i - 1).x);
			final int dy = Math.abs(path.get(i).y - path.get(i - 1).y);
			assertTrue("step " + i + " jumps tiles", dx <= 1 && dy <= 1);
			assertTrue("step " + i + " goes nowhere", dx + dy > 0);
		}
	}
}
