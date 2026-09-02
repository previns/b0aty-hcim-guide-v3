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
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
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

	/** The stairs or ladder to take, or null when the target is reachable. */
	public TileObject getApproach()
	{
		return approach;
	}

	public void clear()
	{
		approach = null;
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
		final Player player = client.getLocalPlayer();
		final String stepId = step == null ? null : step.getId();
		final WorldPoint at = player == null ? null : player.getWorldLocation();
		if (Objects.equals(stepId, lastStepId)
			&& Objects.equals(at, lastScanFrom))
		{
			return;
		}
		lastStepId = stepId;
		lastScanFrom = at;

		rescan();
	}

	/** The scene is rebuilt on a loading screen, so anything held is stale. */
	public void onSceneChanged()
	{
		approach = null;
		lastStepId = null;
		lastScanFrom = null;
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
		if (target == null)
		{
			return;
		}

		// Already found in the scene means it is on this floor and reachable.
		if (!tracker.getNpcs().isEmpty() || !tracker.getObjects().isEmpty())
		{
			return;
		}

		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		final WorldPoint here = local.getWorldLocation();

		final Integer targetPlane = planeOf(target);
		if (targetPlane == null || targetPlane == here.getPlane())
		{
			return;
		}

		final String wanted = targetPlane > here.getPlane() ? CLIMB_UP : CLIMB_DOWN;

		// The quest's own answer, when there is one.
		final Approach known = step.getApproach();
		if (known != null)
		{
			approach = fromKnown(known, wanted);
			if (approach != null)
			{
				return;
			}
		}

		approach = nearestClimbable(here, wanted);
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
			if (known.getIds().contains(object.getId()))
			{
				exact.offer(object, 0);
			}
			if (at == null)
			{
				return;
			}
			final int distance = object.getWorldLocation().distanceTo(at);
			if (distance <= KNOWN_POINT_SLACK && climbs(object, direction))
			{
				atThatSpot.offer(object, distance);
			}
		});
		return exact.object != null ? exact.object : atThatSpot.object;
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


	private static Integer planeOf(Target target)
	{
		for (List<Integer> raw : target.getPoints())
		{
			if (raw != null && raw.size() >= 3)
			{
				return raw.get(2);
			}
		}
		return null;
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
