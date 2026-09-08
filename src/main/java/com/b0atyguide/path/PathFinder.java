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
 * <p>Scene-local coordinates throughout (0..103). Search is bounded by the
 * loaded scene and recomputed on relevant movement or scenery changes, not
 * for every rendered frame.
 *
 * <p>Deliberately free of {@code Client}: this is the one piece of the overlay
 * work with real logic in it, and it is worth being able to test on a hand-drawn
 * map.
 */
public final class PathFinder
{
	/** Visible in diagnostics so a live screenshot identifies this Java build. */
	public static final String ROUTING_REVISION = "footprint routing v3.1";

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

	private PathFinder()
	{
	}

	/**
	 * @param flags scene collision flags, [x][y]
	 * @return tiles from start to the destination inclusive, or -- when it
	 *     cannot be reached -- to the reachable tile closest to it; empty only
	 *     when the player cannot get anywhere nearer than where they stand
	 */
	public static List<Point> find(int[][] flags, int startX, int startY, int destX, int destY)
	{
		return find(flags, null, startX, startY, destX, destY);
	}

	/**
	 * As above, but treating closed doors as passable.
	 *
	 * <p>A shut door sets the same wall flags as a solid wall, so a route that
	 * honours collision alone refuses to go through one -- which is why the
	 * line used to vanish whenever a door stood between the player and the
	 * step. A player just opens it, so a door is a step on the route, not the
	 * end of it.
	 *
	 * @param doors scene-sized mask, true where a door stands; may be null
	 */
	public static List<Point> find(int[][] flags, int[][] doors,
		int startX, int startY, int destX, int destY)
	{
		return find(flags, doors, startX, startY, PathTarget.tile(destX, destY));
	}

	/** Search to any legal interaction tile of the actual destination bounds. */
	private static List<Point> find(int[][] flags, int[][] doors,
		int startX, int startY, PathTarget target)
	{
		if (flags == null
			|| outside(startX, startY) || target == null || !target.isValid()
			|| flags.length < SCENE)
		{
			return Collections.emptyList();
		}
		if (target.reached(flags, startX, startY))
		{
			return Collections.singletonList(new Point(startX, startY));
		}

		final int[] cameFrom = new int[SCENE * SCENE];
		java.util.Arrays.fill(cameFrom, -1);
		final int start = index(startX, startY);
		cameFrom[start] = start;

		// Every tile enters the queue at most once. A primitive queue avoids
		// allocating an Integer for every visited tile. BFS layer boundaries
		// supply walking cost without a third scene-sized distance array.
		final int[] queue = new int[SCENE * SCENE];
		int head = 0;
		int tail = 1;
		queue[0] = start;
		int layerEnd = 1;
		int distance = 0;

		// The reachable tile that ends up closest to the goal. Most of this
		// guide's destinations are somewhere the player cannot yet stand on --
		// past the edge of the loaded scene, inside a building, on the far bank
		// of a river -- and returning nothing there left the route invisible on
		// exactly the steps that are a long walk.
		//
		// Shortest Path settles the ties, in its order: nearest to the goal,
		// then the shortest walk to get there, then lowest x, then lowest y.
		// Two tiles are equally close often -- a doorway is flanked by them --
		// and picking arbitrarily made the end of the line jump about between
		// ticks as the scene reloaded.
		int nearest = start;
		int nearestGap = target.gap(startX, startY);
		int nearestCost = 0;

		while (head < tail)
		{
			if (head == layerEnd)
			{
				distance++;
				layerEnd = tail;
			}
			final int current = queue[head++];
			if (target.reached(flags, current % SCENE, current / SCENE))
			{
				return reconstruct(cameFrom, start, current);
			}
			final int x = current % SCENE;
			final int y = current / SCENE;

			if (closer(current, distance, nearest, nearestGap, nearestCost,
				target))
			{
				nearest = current;
				nearestGap = target.gap(x, y);
				nearestCost = distance;
			}

			for (int dir = 0; dir < DX.length; dir++)
			{
				final int nx = x + DX[dir];
				final int ny = y + DY[dir];
				if (outside(nx, ny) || cameFrom[index(nx, ny)] != -1)
				{
					continue;
				}
				if (!walkable(flags, doors, x, y, nx, ny, dir))
				{
					continue;
				}
				cameFrom[index(nx, ny)] = current;
				queue[tail++] = index(nx, ny);
			}
		}

		// The goal is walled off or off the scene. Walk as far towards it as
		// the player actually can: that part of the route is true, and it is
		// what they need while crossing the map. Nothing is drawn when the
		// nearest reachable tile is the one they are standing on.
		return nearest == start
			? Collections.emptyList()
			: reconstruct(cameFrom, start, nearest);
	}

	/**
	 * A route, and the way in when the destination is walled off.
	 *
	 * <p>A fenced paddock with the npc inside it used to produce a line to the
	 * nearest tile of fence on the side the npc happened to be, which reads as
	 * "walk into this fence". The player is being told where the thing is, not
	 * how to reach it, and that is worse than no line: it is a line that lies.
	 */
	public static final class Route
	{
		private final List<Point> path;
		private final Point entrance;
		private final String decision;
		private final String graphCheck;

		Route(List<Point> path, Point entrance, String decision, String graphCheck)
		{
			this.path = path;
			this.entrance = entrance;
			this.decision = decision;
			this.graphCheck = graphCheck;
		}

		/** The tiles to draw, ending at the destination or at the way in. */
		public List<Point> getPath()
		{
			return path;
		}

		/**
		 * The door, gate or stile this route stops at, or null.
		 *
		 * <p>Set when a closed entry is on the best route, including when a much
		 * longer strict route could walk around the enclosure. The overlay marks
		 * it, because a line ending at a gate says nothing on its own about which
		 * gate.
		 */
		public Point getEntrance()
		{
			return entrance;
		}

		/** Which routing branch produced the visible line. */
		public String getDecision()
		{
			return decision;
		}

		/** What the applicable verified or live-action graph established. */
		public String getGraphCheck()
		{
			return graphCheck;
		}
	}

	/**
	 * An interactable entry and the ordinary ground from which the player can
	 * reach it.
	 *
	 * <p>The object's anchor is not necessarily a tile the player can stand on.
	 * Wall objects in particular may be anchored on the far side of their
	 * collision edge. Keeping the approach tile separately prevents a route to
	 * the object coordinate from falling back to an unrelated piece of wall.
	 */
	private static final class Entry
	{
		private final Point object;
		private final Point approach;

		private Entry(Point object, Point approach)
		{
			this.object = object;
			this.approach = approach;
		}
	}

	/**
	 * The way to a destination, going through the enclosure it sits in.
	 *
	 * <p>Three answers. A verified closed entry on the shortest augmented route
	 * comes first: a destination can be reachable only by taking a long walk
	 * around a fence while the nearby gate is still the route a player should
	 * use. Otherwise this returns the strict route to the destination, or the
	 * nearest live-action entry into a genuinely sealed region. Only when none
	 * of those is proven does it return the strict best-effort partial route.
	 */
	public static Route routeTo(int[][] flags, int[][] doors, int[][] ways,
		int startX, int startY, int destX, int destY)
	{
		return routeTo(flags, doors, ways, null,
			startX, startY, destX, destY);
	}

	/**
	 * As above, with verified transport edges for loaded actionable entries.
	 *
	 * <p>A closed object's collision is the state before the interaction. It is
	 * not always possible to manufacture the post-Open state by relaxing one
	 * flag on that map. A verified transport says which two sides the action
	 * joins, so those edges participate directly in an augmented route graph
	 * rather than requiring invented open-state collision.
	 */
	public static Route routeTo(int[][] flags, int[][] doors, int[][] ways,
		int[][] verifiedEntries,
		int startX, int startY, int destX, int destY)
	{
		return routeTo(flags, doors, ways, verifiedEntries, startX, startY,
			PathTarget.location(flags, destX, destY));
	}

	/**
	 * Route to the footprint supplied by the live scene. The exact same target
	 * predicate is used for strict walking, transport search and final arrival;
	 * a successful gate route cannot be rejected by a separate centre-distance
	 * approximation after the search has finished.
	 */
	public static Route routeTo(int[][] flags, int[][] doors, int[][] ways,
		int[][] verifiedEntries, int startX, int startY, PathTarget target)
	{
		if (flags == null || flags.length < SCENE || outside(startX, startY)
			|| target == null || !target.isValid())
		{
			return new Route(Collections.emptyList(), null, "invalid scene target", "not run");
		}
		// A closed gate is an action the player must take, not ground the route
		// may walk over. Earlier code used the door mask here, then tried to
		// truncate the made-up route at its first artificial crossing. That
		// loses when the collision map chooses a different outside wall as its
		// nearest fallback, which is the live "walk to the fence" failure.
		//
		// Closed entries participate only in the augmented searches below; they
		// never appear in the path returned for drawing. Defer the strict target
		// search: when a gate is found, flooding the entire outside first would
		// compute a partial path that is immediately discarded.
		final boolean hasEntries = anyWays(doors);

		// Verified transports participate in the route before an ordinary path is
		// accepted. A strict route may be able to walk all the way around a long
		// fence; returning it here used to suppress a much shorter route through a
		// closed gate. The augmented search is a superset of strict walking, so if
		// its shortest answer uses a closed verified edge, that interaction really
		// is the next action on the better route.
		final EntrySearch verified = hasEntries
			? entryOnAugmentedRoute(flags, verifiedEntries,
				startX, startY, target, "verified")
			: new EntrySearch(null, "not run: no actionable entries");
		if (verified.entry != null)
		{
			return routeToEntry(flags, target, startX, startY, verified.entry,
				"verified entry", verified.result);
		}

		// The imported table is deliberately evidence, not a completeness claim.
		// New game content and ordinary doors which Shortest Path's static map
		// already treats as open may have no row in it. Give every live action the
		// same graph test using directions derived from its current collision and
		// orientation. Keeping this second means a guessed live orientation cannot
		// beat an explicit verified edge when both solve the route.
		final EntrySearch live = hasEntries
			? entryOnAugmentedRoute(flags, doors,
				startX, startY, target, "live-action")
			: new EntrySearch(null, "not run: no actionable entries");
		if (live.entry != null)
		{
			return routeToEntry(flags, target, startX, startY, live.entry,
				"live-action entry", live.result);
		}

		final List<Point> direct = find(flags, null, startX, startY, target);
		if (!direct.isEmpty())
		{
			final Point end = direct.get(direct.size() - 1);
			if (target.reached(flags, end.x, end.y))
			{
				return new Route(direct, null, "strict target", live.result);
			}
		}

		// Only worth asking when there is something to find. Both floods walk the
		// whole scene, and this runs on every step the player takes towards an
		// unreachable destination -- which is most of a long walk.
		if (!hasEntries)
		{
			return new Route(direct, null, "strict partial", live.result);
		}
		final Entry way = entrance(flags, doors, ways,
			startX, startY, target);
		if (way == null)
		{
			return new Route(direct, null, "strict partial; no entry proven",
				live.result);
		}
		return routeToEntry(flags, target, startX, startY, way,
			"live-action region fallback", live.result);
	}

	/** Draw the strict route to the proven player-side tile of an entry. */
	private static Route routeToEntry(int[][] flags, PathTarget target,
		int startX, int startY, Entry way, String decision, String graphCheck)
	{
		// Route to a tile that the strict player flood proved reachable beside the
		// entry, not to the object's anchor. Asking find() for a blocked anchor
		// returns its generic "closest to the destination" fallback, which can be
		// another piece of fence and recreates the exact lie this feature exists to
		// remove. The object coordinate remains separate for clickbox highlighting.
		final List<Point> toTheDoor = find(flags, null, startX, startY,
			way.approach.x, way.approach.y);
		if (toTheDoor.isEmpty())
		{
			return new Route(find(flags, null, startX, startY, target), null,
				decision + " was unreachable", graphCheck);
		}
		final Point reached = toTheDoor.get(toTheDoor.size() - 1);
		return reached.equals(way.approach)
			? new Route(toTheDoor, way.object, decision, graphCheck)
			: new Route(find(flags, null, startX, startY, target), null,
				decision + " was not reached", graphCheck);
	}

	/** The result of putting one set of actionable entry edges in the graph. */
	private static final class EntrySearch
	{
		private final Entry entry;
		private final String result;

		private EntrySearch(Entry entry, String result)
		{
			this.entry = entry;
			this.result = result;
		}
	}

	/**
	 * Select the first actionable transport on a real route to the destination.
	 *
	 * <p>This is the distinction that makes Shortest Path work: a verified
	 * transport is an edge in the route graph, not a hint to compare against two
	 * independently flooded regions. The latter rejects valid gates whenever
	 * their anchor is not adjacent to the component guessed for the target, and
	 * it cannot handle two closed entries in sequence at all. Route with the
	 * supplied edges, then stop before the first one whose collision edge is
	 * actually closed. For the first pass those edges are imported and verified;
	 * for the second they are inferred from every live Open/Slash-like object.
	 * The edge's object endpoint is highlighted while the tile before it is the
	 * player's exact reachable approach.
	 */
	private static EntrySearch entryOnAugmentedRoute(int[][] flags, int[][] entries,
		int startX, int startY, PathTarget target, String kind)
	{
		if (entries == null)
		{
			return new EntrySearch(null, "not run: no " + kind + " entries");
		}

		final List<Point> through = find(flags, entries,
			startX, startY, target);
		if (through.isEmpty())
		{
			return new EntrySearch(null, "empty " + kind + " route");
		}
		final Point end = through.get(through.size() - 1);
		if (!target.reached(flags, end.x, end.y))
		{
			// find() also returns a best-effort partial route. It proves a
			// transport leads to this destination only when the augmented route
			// actually arrives at the target or its interaction footprint.
			return new EntrySearch(null, kind + " route partial; gap "
				+ (int) Math.ceil(Math.sqrt(target.gap(end.x, end.y))));
		}

		for (int i = 1; i < through.size(); i++)
		{
			final Point from = through.get(i - 1);
			final Point to = through.get(i);
			final Entry entry = closedTransportOnStep(flags, entries, from, to);
			if (entry != null)
			{
				return new EntrySearch(entry, "used closed " + kind + " edge");
			}
		}
		return new EntrySearch(null, kind + " route needed no closed edge");
	}

	/**
	 * The closed verified edge used by one stored route step, if any.
	 *
	 * <p>A diagonal is legal only when both cardinal ways around its corner are
	 * legal. A two-wide gate can therefore make one diagonal route step use the
	 * left and right gate edges internally. Looking only for a transport from
	 * the diagonal's start straight to its end sees neither and loses the gate.
	 * Check the four cardinal component edges in the same shape as
	 * {@link #walkable} and return the first interaction reachable before the
	 * crossing.
	 */
	private static Entry closedTransportOnStep(int[][] flags,
		int[][] verifiedEntries, Point from, Point to)
	{
		final int direction = direction(from, to);
		if (direction < 0)
		{
			return null;
		}
		if (direction < 4)
		{
			return closedTransportOnEdge(flags, verifiedEntries, from, to);
		}

		final Point horizontal = new Point(to.x, from.y);
		final Point vertical = new Point(from.x, to.y);
		Entry entry = closedTransportOnEdge(flags, verifiedEntries, from, horizontal);
		if (entry == null)
		{
			entry = closedTransportOnEdge(flags, verifiedEntries, from, vertical);
		}
		if (entry == null)
		{
			entry = closedTransportOnEdge(flags, verifiedEntries, horizontal, to);
		}
		if (entry == null)
		{
			entry = closedTransportOnEdge(flags, verifiedEntries, vertical, to);
		}
		return entry;
	}

	private static Entry closedTransportOnEdge(int[][] flags,
		int[][] verifiedEntries, Point from, Point to)
	{
		final Point object = transportObject(verifiedEntries, from, to);
		if (object == null)
		{
			return null;
		}
		final int direction = direction(from, to);
		return direction >= 0 && direction < 4
			&& !walkable(flags, null, from.x, from.y, to.x, to.y, direction)
			? new Entry(object, from) : null;
	}

	/** The live object endpoint of a cardinal verified transport edge. */
	private static Point transportObject(int[][] verifiedEntries, Point from, Point to)
	{
		final int direction = direction(from, to);
		if (direction < 0 || direction >= 4)
		{
			return null;
		}
		final int movement = directionFlag(direction);
		if ((verifiedEntries[from.x][from.y] & movement) != 0)
		{
			return from;
		}
		if ((verifiedEntries[to.x][to.y] & opposite(movement)) != 0)
		{
			return to;
		}
		return null;
	}

	private static int direction(Point from, Point to)
	{
		final int dx = to.x - from.x;
		final int dy = to.y - from.y;
		for (int direction = 0; direction < DX.length; direction++)
		{
			if (DX[direction] == dx && DY[direction] == dy)
			{
				return direction;
			}
		}
		return -1;
	}

	private static int directionFlag(int direction)
	{
		return direction == 0 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH
			: direction == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_EAST
			: direction == 2 ? CollisionDataFlag.BLOCK_MOVEMENT_SOUTH
			: CollisionDataFlag.BLOCK_MOVEMENT_WEST;
	}

	/**
	 * The cheapest tile in an object's cardinal interaction footprint that a
	 * collision flood reaches.
	 *
	 * <p>Centre first only settles equal-cost ties. Closed wall anchors are
	 * normally unreachable, so the selected point is the reachable side from
	 * which the player can click Open, Slash, Climb-over, and similar actions.
	 */
	private static Point nearestAround(int[] region, int x, int y)
	{
		Point best = null;
		int bestCost = Integer.MAX_VALUE;
		if (reachable(region, x, y))
		{
			best = new Point(x, y);
			bestCost = region[index(x, y)];
		}
		for (int direction = 0; direction < 4; direction++)
		{
			final int aroundX = x + DX[direction];
			final int aroundY = y + DY[direction];
			if (!reachable(region, aroundX, aroundY))
			{
				continue;
			}
			final int cost = region[index(aroundX, aroundY)];
			if (cost < bestCost)
			{
				best = new Point(aroundX, aroundY);
				bestCost = cost;
			}
		}
		return best;
	}

	private static boolean reachable(int[] region, int x, int y)
	{
		return !outside(x, y) && region[index(x, y)] >= 0;
	}

	/**
	 * The nearest openable thing standing between the player and the goal.
	 *
	 * <p>The target region is flooded with ordinary walking rules. Each entry
	 * candidate is then opened with its connected cluster of adjacent actionable
	 * wall segments for a trial route. A double gate is two {@code WallObject}s,
	 * not one: opening only its left half in the collision model still leaves
	 * the right half sealing the crossing. A candidate counts only when that
	 * cluster reaches the target region. This is more reliable than requiring
	 * the candidate's anchor tile to touch both regions: large and
	 * corner-anchored game objects commonly put that anchor beside, rather than
	 * on, the edge a player crosses.
	 *
	 * <p>{@code ways} is deliberately separate from normal movement. This runs
	 * against everything that looks like a way in, including a closed Open gate,
	 * and the answer is marked on screen rather than walked through.
	 *
	 * <p>Null when there is no way in -- across a river, up a staircase, behind
	 * something that is not a door. The caller then falls back to walking as
	 * far as it can, which is all anyone can honestly say.
	 */
	private static Entry entrance(int[][] flags, int[][] doors, int[][] ways,
		int startX, int startY, PathTarget target)
	{
		if (ways == null)
		{
			return null;
		}
		// This flood describes the destination's sealed region. Opening every
		// actionable object here joins that region to the outside before we test
		// a candidate, after which an unrelated nearby door can appear to work.
		// Keep it collision-only; each candidate is opened separately below.
		final int[] fromGoal = floodToTarget(flags, target);
		final int[] fromPlayer = floodFromPlayer(flags, startX, startY);
		if (fromGoal == null || fromPlayer == null)
		{
			return null;
		}

		Entry best = null;
		int bestCost = Integer.MAX_VALUE;
		for (int y = 0; y < SCENE; y++)
		{
			for (int x = 0; x < SCENE; x++)
			{
				// Names such as "Fence" are useful diagnostics, but an Inspect-only
				// wall is not a way through. Only a live object with an allowed
				// crossing action is eligible to become the next step.
				if (ways[x][y] == 0 || doors == null || doors[x][y] == 0)
				{
					continue;
				}
				// Do not pretend every closed entry is ground. Open only this
				// actionable entry and its touching halves, then ask whether that
				// real gate reaches the destination's region.
				final int[][] openedHere = openingAt(doors, ways, x, y);
				final List<Point> through = find(flags, openedHere,
					startX, startY, target);
				if (through.isEmpty())
				{
					continue;
				}
				final Point reached = through.get(through.size() - 1);
				// Adjacency is not enough: the tile nearest the target is often on
				// the wrong side of the very wall we are trying to cross. The trial
				// must genuinely enter the destination's collision-connected region.
				if (fromGoal[index(reached.x, reached.y)] < 0)
				{
					continue;
				}

				// Pick the entry that can be approached most cheaply without
				// opening anything. The route itself remains collision-only and
				// stops on the player-facing side for the Open action.
				final Point approach = nearestAround(fromPlayer, x, y);
				if (approach == null)
				{
					continue;
				}
				final int reach = fromPlayer[index(approach.x, approach.y)];
				if (reach < bestCost)
				{
					bestCost = reach;
					best = new Entry(new Point(x, y), approach);
				}
			}
		}
		return best;
	}

	/**
	 * Collision relaxations for one interactable entry, including a double gate
	 * or a run of wall segments that are one physical opening.
	 *
	 * <p>Only {@code doors} participates in the expansion. {@code ways} also
	 * contains name-only fallbacks such as a fence or archway; joining those
	 * would create an imaginary opening across ordinary scenery.
	 */
	private static int[][] openingAt(int[][] doors, int[][] ways, int startX, int startY)
	{
		final int[][] opening = new int[SCENE][SCENE];
		opening[startX][startY] = doors == null ? 0 : doors[startX][startY];
		if (doors == null || doors[startX][startY] == 0)
		{
			return opening;
		}

		final boolean[] seen = new boolean[SCENE * SCENE];
		final Deque<Integer> queue = new ArrayDeque<>();
		final int start = index(startX, startY);
		seen[start] = true;
		queue.add(start);
		while (!queue.isEmpty())
		{
			final int at = queue.remove();
			final int x = at % SCENE;
			final int y = at / SCENE;
			opening[x][y] = doors[x][y];
			for (int direction = 0; direction < 4; direction++)
			{
				final int nextX = x + DX[direction];
				final int nextY = y + DY[direction];
				if (outside(nextX, nextY))
				{
					continue;
				}
				final int next = index(nextX, nextY);
				if (!seen[next] && doors[nextX][nextY] != 0)
				{
					seen[next] = true;
					queue.add(next);
				}
			}
		}
		return opening;
	}

	/** Whether the scene holds anything that could be a way in at all. */
	private static boolean anyWays(int[][] ways)
	{
		if (ways == null)
		{
			return false;
		}
		for (int[] column : ways)
		{
			for (int value : column)
			{
				if (value != 0)
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Reverse reachability from all legal interaction tiles. A blocked centre
	 * and its four neighbours are not seeds: all five can be inside a large
	 * object. Test predecessor-to-current movement when reversing the graph,
	 * since collision flags are read on the tile being entered.
	 */
	private static int[] floodToTarget(int[][] flags, PathTarget target)
	{
		final int[] cost = new int[SCENE * SCENE];
		java.util.Arrays.fill(cost, -1);
		final Deque<Integer> queue = new ArrayDeque<>();
		for (int y = 0; y < SCENE; y++)
		{
			for (int x = 0; x < SCENE; x++)
			{
				if (target.reached(flags, x, y))
				{
					cost[index(x, y)] = 0;
					queue.add(index(x, y));
				}
			}
		}
		while (!queue.isEmpty())
		{
			final int current = queue.remove();
			final int x = current % SCENE;
			final int y = current / SCENE;
			for (int dir = 0; dir < DX.length; dir++)
			{
				final int nx = x - DX[dir];
				final int ny = y - DY[dir];
				if (outside(nx, ny) || cost[index(nx, ny)] >= 0
					|| (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0
					|| !walkable(flags, null, nx, ny, x, y, dir))
				{
					continue;
				}
				cost[index(nx, ny)] = cost[current] + 1;
				queue.add(index(nx, ny));
			}
		}
		return cost;
	}

	/** Walking cost to every tile reachable from one, or -1 where it is not. */
	private static int[] floodFromPlayer(int[][] flags, int fromX, int fromY)
	{
		if (outside(fromX, fromY))
		{
			return null;
		}
		final int[] cost = new int[SCENE * SCENE];
		java.util.Arrays.fill(cost, -1);
		final Deque<Integer> queue = new ArrayDeque<>();
		final int start = index(fromX, fromY);
		cost[start] = 0;
		queue.add(start);

		// Never seed neighbouring tiles: the first player step must respect
		// collision just like every later step. Goal interaction tiles are
		// handled separately by floodToTarget, using the actual footprint.
		int visited = 0;
		while (!queue.isEmpty() && visited++ < MAX_VISITED)
		{
			final int current = queue.poll();
			final int x = current % SCENE;
			final int y = current / SCENE;
			for (int dir = 0; dir < DX.length; dir++)
			{
				final int nx = x + DX[dir];
				final int ny = y + DY[dir];
				if (outside(nx, ny) || cost[index(nx, ny)] >= 0)
				{
					continue;
				}
				if (!walkable(flags, null, x, y, nx, ny, dir))
				{
					continue;
				}
				cost[index(nx, ny)] = cost[current] + 1;
				queue.add(index(nx, ny));
			}
		}
		return cost;
	}

	/**
	 * Whether one reachable tile is a better place to stop than another.
	 *
	 * <p>Shortest Path's order, and the order matters: closest to the goal
	 * first, because that is what the player is being pointed at; then the
	 * shortest walk, so the line does not wander to reach an equally close
	 * tile; then x and then y, which decide nothing except that the same input
	 * always gives the same answer.
	 */
	private static boolean closer(int candidate, int candidateCost,
		int best, int bestGap, int bestCost, PathTarget target)
	{
		final int away = target.gap(candidate % SCENE, candidate / SCENE);
		if (away != bestGap)
		{
			return away < bestGap;
		}
		if (candidateCost != bestCost)
		{
			return candidateCost < bestCost;
		}
		final int x = candidate % SCENE;
		final int bestX = best % SCENE;
		if (x != bestX)
		{
			return x < bestX;
		}
		return candidate / SCENE < best / SCENE;
	}

	/**
	 * One step's legality, for a test.
	 *
	 * <p>The whole route is emergent, so a rule about a single edge is only
	 * testable by asking about that edge. Direction is an index into
	 * {@link #DX} / {@link #DY}: 0 north, 1 east, 2 south, 3 west.
	 */
	public static boolean walkableForTest(int[][] flags, int[][] doors,
		int x, int y, int nx, int ny, int dir)
	{
		return walkable(flags, doors, x, y, nx, ny, dir);
	}

	/**
	 * Whether a step from one tile to its neighbour is legal.
	 *
	 * <p>The rules are Shortest Path's (BSD 2-Clause, see NOTICE.md), read from
	 * its {@code CollisionMap} and expressed against the client's live flags
	 * rather than its own prebuilt map. Two of them this did not have.
	 *
	 * <p><b>A diagonal is two ways round the corner, and the game requires
	 * both.</b> Going north-east means north-then-east <em>and</em>
	 * east-then-north must each be clear. Checking only "can I leave north" and
	 * "can I leave east" misses a wall on the far side of the corner, and the
	 * route then walks the player into it.
	 *
	 * <p>Shortest Path has a second rule -- a tile with no traversable direction
	 * still yields its neighbours -- and that one is <b>not</b> ported. Porting
	 * it was a mistake that shipped. Their map stores what <em>is</em>
	 * traversable, so a tile with nothing set is one their map does not cover.
	 * The client's flags are the opposite polarity: a tile with every direction
	 * blocked is a tile with walls on all four sides. The same rule therefore
	 * read as "walk through them", and the route did.
	 */
	private static boolean walkable(int[][] flags, int[][] doors,
		int x, int y, int nx, int ny, int dir)
	{
		final int dx = DX[dir];
		final int dy = DY[dir];

		if (dir < 4)
		{
			return passable(flags, doors, x, y, dx, dy);
		}

		return passable(flags, doors, x, y, dx, 0)
			&& passable(flags, doors, x + dx, y, 0, dy)
			&& passable(flags, doors, x, y, 0, dy)
			&& passable(flags, doors, x, y + dy, dx, 0)
			&& diagonalDestinationClear(flags, nx, ny, dx, dy);
	}

	private static boolean diagonalDestinationClear(int[][] flags,
		int x, int y, int dx, int dy)
	{
		final int corner = dx > 0
			? (dy > 0 ? CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST
				: CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST)
			: (dy > 0 ? CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST
				: CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST);
		return (flags[x][y] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | corner)) == 0;
	}

	private static boolean passable(int[][] flags, int[][] doors, int x, int y, int dx, int dy)
	{
		if (outside(x, y))
		{
			return false;
		}
		final int nx = x + dx;
		final int ny = y + dy;
		if (outside(nx, ny))
		{
			return false;
		}
		final int movement = dx > 0 ? CollisionDataFlag.BLOCK_MOVEMENT_EAST
			: dx < 0 ? CollisionDataFlag.BLOCK_MOVEMENT_WEST
			: dy > 0 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH
			: CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		if (door(doors, x, y, nx, ny, movement))
		{
			return true;
		}

		// RuneLite's WorldArea movement contract reads the tile being entered:
		// moving north is blocked by SOUTH on the northern tile, east by WEST
		// on the eastern tile, and so on. Reading only the tile being left made
		// collision asymmetric; a player flood could stop at a fence while the
		// reverse target flood leaked through the same edge.
		final int destinationMask = CollisionDataFlag.BLOCK_MOVEMENT_FULL
			| opposite(movement);
		return (flags[nx][ny] & destinationMask) == 0;
	}

	/**
	 * Whether a door on this edge opens the way through it.
	 *
	 * <p>The direction matters. A doorway tile in the corner of a room has a
	 * door on one side and solid wall on another, and treating "there is a door
	 * somewhere on this tile" as permission to leave in any direction put the
	 * route through the stonework beside the door.
	 *
	 * <p>Both tiles are asked, because the game records a wall against the tile
	 * on each side of it under opposite flags.
	 */
	private static boolean door(int[][] doors, int x, int y, int nx, int ny, int blocking)
	{
		return doors != null
			&& ((doors[x][y] & blocking) != 0 || (doors[nx][ny] & opposite(blocking)) != 0);
	}

	/** The same edge, seen from the tile on the other side of it. */
	private static int opposite(int blocking)
	{
		switch (blocking)
		{
			case CollisionDataFlag.BLOCK_MOVEMENT_NORTH:
				return CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
			case CollisionDataFlag.BLOCK_MOVEMENT_SOUTH:
				return CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
			case CollisionDataFlag.BLOCK_MOVEMENT_EAST:
				return CollisionDataFlag.BLOCK_MOVEMENT_WEST;
			default:
				return CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		}
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
