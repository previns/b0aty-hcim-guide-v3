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
package com.b0atyguide.overlay;

import com.b0atyguide.data.Approach;
import com.b0atyguide.data.Destination;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import com.b0atyguide.path.RealPoint;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

/**
 * The way up or down, when the step's target is on another floor.
 *
 * <p>"Go upstairs and talk to Duke Horacio" resolves to Duke Horacio at plane
 * 1. Stood on plane 0 the player cannot walk there, cannot see him, and the
 * arrow points at a spot on the ground floor where nothing is. What they
 * actually need is the staircase.
 *
 * <p>Two sources, in order:
 *
 * <ol>
 *   <li>The step's own {@code approach}, worked out at build time from the
 *       Quest Helper for the quest the step names. That is the staircase the
 *       quest actually uses. Only trusted when the object is really in the
 *       scene, matched by id -- the build cannot know what the player can see.
 *   <li>Failing that, the nearest object the game says can be climbed, found
 *       through {@link ObjectComposition#getActions()} rather than a list of
 *       names. Asking the game is authoritative; a name list silently misses
 *       whatever it forgot.
 * </ol>
 *
 * <p>The fallback picks the nearest, which is right in open country and wrong
 * in Lumbridge castle, where several staircases sit within a few tiles. That is
 * exactly why the curated source is tried first -- and why either way it is
 * drawn as a "way up" hint rather than as the step's target.
 */
@Singleton
public class ApproachTracker
{
	private static final String CLIMB_UP = "up";
	private static final String CLIMB_DOWN = "down";

	/**
	 * Only consider stairs this close. Beyond it the nearest climbable object
	 * is more likely to belong to a different building than to the route.
	 */
	private static final int MAX_TILES = 25;

	/**
	 * How near a target has to be for its floor to be the thing in the way.
	 *
	 * <p>Wider than the loaded scene on purpose: a target at the edge of it is
	 * still somewhere the player can see, and its stairs are still the answer.
	 */
	private static final int SAME_PLACE = 128;

	/**
	 * How far from the recorded coordinate a climbable object may sit and still
	 * be the one meant. Small: this is correcting for an id that names a
	 * sibling variant, not searching for a different staircase.
	 */
	private static final int KNOWN_POINT_SLACK = 3;

	@Inject
	private Client client;

	@Inject
	private SceneTracker tracker;

	private TileObject approach;

	/** What the held approach was found for; null means it is not valid. */
	private String lastStepId;
	private WorldPoint lastScanFrom;
	private Object lastInstruction;

	/**
	 * Where the map keeps its basements, dungeons and caves.
	 *
	 * <p>Everything walkable on the surface is south of this.
	 */
	private static final int UNDERGROUND_Y = 6400;

	/** The stairs or ladder to take, or null when the target is reachable. */
	public TileObject getApproach()
	{
		return approach;
	}

	public void clear()
	{
		onSceneChanged();
	}

	/**
	 * Call from a game tick.
	 *
	 * <p>Like the bank scan, this walks the whole scene, so it is limited to
	 * the moments its answer can change: a different step, or the player having
	 * moved. Stairs do not wander.
	 */
	public void update()
	{
		final Step step = tracker.getStep();
		final String stepId = step == null ? null : step.getId();
		final WorldPoint at = RealPoint.of(client, client.getLocalPlayer());
		if (Objects.equals(stepId, lastStepId)
			&& tracker.getNavigationInstruction() == lastInstruction
			&& Objects.equals(at, lastScanFrom))
		{
			return;
		}
		lastStepId = stepId;
		lastScanFrom = at;
		lastInstruction = tracker.getNavigationInstruction();

		rescan();
	}

	/** The scene is rebuilt on a loading screen, so anything held is stale. */
	public void onSceneChanged()
	{
		approach = null;
		lastStepId = null;
		lastScanFrom = null;
		lastInstruction = null;
	}

	private void rescan()
	{
		approach = null;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final Step step = tracker.getStep();
		final Target target = step == null ? null : step.getTarget();
		if (target == null || target.isScattered() || tracker.getNavigationInstruction() != null)
		{
			return;
		}

		// Already found in the scene means it is on this floor and reachable.
		if (!tracker.getNpcs().isEmpty() || !tracker.getObjects().isEmpty())
		{
			return;
		}

		final WorldPoint here = RealPoint.of(client, client.getLocalPlayer());
		if (here == null)
		{
			return;
		}

		final Integer targetLevel = levelOf(target);
		if (targetLevel == null || targetLevel == levelOf(here))
		{
			return;
		}

		// And only when the level is what is actually stopping the player. A
		// staircase is the answer to "the thing is above me", not to "the thing
		// is six hundred tiles west and happens to be upstairs" -- and asked
		// the second way this pointed at whatever ladder was nearest, so a step
		// reading "Head to the Grand Tree" sent players to a staircase in
		// Edgeville. Walking there comes first; the stairs are a problem for
		// when they arrive.
		if (!withinReach(target, here))
		{
			return;
		}

		final String wanted = targetLevel > levelOf(here) ? CLIMB_UP : CLIMB_DOWN;

		// The quest's own answer, when there is one.
		final Approach known = step.getApproach();
		if (known != null)
		{
			approach = fromKnown(known, wanted);
			// A known staircase outside the scene is not permission to use a
			// different building's ladder. Walk towards the target until it loads.
			return;
		}

		// A step that names somewhere to walk on this level is answered by
		// walking there. "Head to the Grand Tree" points at a spot on the
		// tree's first floor and at its base on the ground; guessing at a
		// staircase sent players round the tree's several staircases instead of
		// to the gate. Forty-six of the 191 steps on another level say this.
		if (walkableGround(step))
		{
			return;
		}

		approach = nearestClimbable(here, wanted);
	}

	/**
	 * Whether the step names somewhere on the ground to head for.
	 *
	 * <p>Then that is the instruction, and the stairs are for after. The guess
	 * below is only worth making when there is nothing better, and in a building
	 * with several staircases it is wrong as often as not.
	 */
	private static boolean walkableGround(Step step)
	{
		final Destination place = step.getDestination();
		if (place == null)
		{
			return false;
		}
		for (List<Integer> raw : place.getPoints())
		{
			if (raw != null && raw.size() >= 3
				&& raw.get(2) == 0 && raw.get(1) < UNDERGROUND_Y)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the target is close enough that its floor is the obstacle.
	 *
	 * <p>Measured flat, ignoring the level, because that is the question: is
	 * this thing in the building the player is standing in, or is it a journey
	 * away. A little wider than the loaded scene, so a target at the far edge of
	 * it still counts.
	 */
	public static boolean withinReach(Target target, WorldPoint here)
	{
		for (List<Integer> raw : target.getPoints())
		{
			if (raw != null && raw.size() >= 3)
			{
				final int away = Math.abs(raw.get(0) - here.getX())
					+ Math.abs(raw.get(1) - here.getY());
				if (away <= SAME_PLACE)
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * How far up or down a point is, counting the underground as below ground.
	 *
	 * <p>Comparing planes alone is not enough. A basement or dungeon is not a
	 * different plane in this game: it is the same plane in a band of the map
	 * far to the north, so Sedridor's basement reads as plane 0 exactly like
	 * the tower above it. Comparing planes therefore decided the player was
	 * already there and pointed at nothing, on every basement, dungeon and cave
	 * in the guide.
	 *
	 * <p>The band starts well above anywhere on the surface -- the furthest
	 * north surface ground sits under 4000 -- so the test cannot mistake a real
	 * place for a cellar.
	 */
	private static int levelOf(WorldPoint point)
	{
		return point.getY() >= UNDERGROUND_Y ? -1 : point.getPlane();
	}

	private static Integer levelOf(Target target)
	{
		for (List<Integer> raw : target.getPoints())
		{
			if (raw != null && raw.size() >= 3)
			{
				return raw.get(1) >= UNDERGROUND_Y ? -1 : raw.get(2);
			}
		}
		return null;
	}

	/**
	 * Resolve the quest's recorded way up against what is actually loaded.
	 *
	 * <p>Tried in order, because the two halves of the record are not equally
	 * reliable. The id can be a sibling of the variant the scene holds -- a
	 * staircase has ten near-identical constants and Quest Helper names
	 * whichever one its quest met -- while the coordinate is where the thing
	 * stands and does not drift. So an exact id wins, and failing that anything
	 * climbable at that spot is taken.
	 *
	 * <p>Nothing here requires the object to look like stairs. Quest Helper
	 * sometimes points at a crate or a hole, because that is genuinely how the
	 * game models a way through; second-guessing it would break those.
	 */
	private TileObject fromKnown(Approach known, String direction)
	{
		final List<Integer> point = known.getPoint();
		final WorldPoint at = point.size() < 3
			? null
			: new WorldPoint(point.get(0), point.get(1), point.get(2));

		final Nearest exact = new Nearest();
		final Nearest atThatSpot = new Nearest();
		SceneObjects.forEach(client, object ->
		{
			final int distance = knownPointDistance(at, RealPoint.of(client, object.getWorldLocation()));
			if (distance == Integer.MAX_VALUE)
			{
				return;
			}
			if (known.getIds().contains(object.getId()))
			{
				exact.offer(object, distance);
			}
			if (climbs(object, direction))
			{
				atThatSpot.offer(object, distance);
			}
		});
		return exact.object != null ? exact.object : atThatSpot.object;
	}

	/** IDs such as LADDER repeat throughout the world; the recorded place must match too. */
	public static int knownPointDistance(WorldPoint expected, WorldPoint candidate)
	{
		if (expected == null || candidate == null)
		{
			return Integer.MAX_VALUE;
		}
		final int distance = candidate.distanceTo(expected);
		return distance <= KNOWN_POINT_SLACK ? distance : Integer.MAX_VALUE;
	}

	/** The closest object offered so far. Mutable so a lambda can fill it in. */
	private static final class Nearest
	{
		private TileObject object;
		private int distance = Integer.MAX_VALUE;

		void offer(TileObject candidate, int candidateDistance)
		{
			if (candidateDistance < distance)
			{
				distance = candidateDistance;
				object = candidate;
			}
		}
	}

	/**
	 * @param direction "up" or "down"; matched inside the action text, which is
	 *     "Climb-up", "Climb up", "Walk-up" and several other spellings
	 */
	private TileObject nearestClimbable(WorldPoint from, String direction)
	{
		final Nearest best = new Nearest();
		SceneObjects.forEach(client, object ->
			best.offer(object, distanceIfClimbable(object, from, direction)));
		return best.object;
	}

	private int distanceIfClimbable(TileObject object, WorldPoint from, String direction)
	{
		if (object == null)
		{
			return Integer.MAX_VALUE;
		}
		final int distance = object.getWorldLocation().distanceTo(from);
		if (distance > MAX_TILES)
		{
			return Integer.MAX_VALUE;
		}
		return climbs(object, direction) ? distance : Integer.MAX_VALUE;
	}

	/** Whether the game offers a climb in this direction on this object. */
	private boolean climbs(TileObject object, String direction)
	{
		// getObjectDefinition must run on the client thread, which every caller
		// of this method already does.
		final ObjectComposition composition = SceneObjects.definitionOf(client, object);
		if (composition == null)
		{
			return false;
		}
		final String[] actions = composition.getActions();
		return SceneObjects.hasAction(actions, "climb", direction)
			|| SceneObjects.hasAction(actions, "walk", direction);
	}
}
