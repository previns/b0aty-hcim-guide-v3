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

import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import com.b0atyguide.overlay.ApproachTracker;
import com.b0atyguide.overlay.SceneTracker;
import java.awt.Point;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Keeps a current walkable route from the player to the step's target.
 *
 * <p>Recomputed only when the player changes tile or the target moves, not per
 * frame: the search is cheap but an overlay renders many times a tick.
 */
@Singleton
public class PathTracker
{
	@Inject
	private Client client;

	@Inject
	private SceneTracker tracker;

	@Inject
	private ApproachTracker approachTracker;

	private List<Point> path = Collections.emptyList();
	private WorldPoint lastFrom;
	private WorldPoint lastTo;

	/** Scene-local tiles from the player to the target, or empty. */
	public List<Point> getPath()
	{
		return path;
	}

	public void clear()
	{
		path = Collections.emptyList();
		lastFrom = null;
		lastTo = null;
	}

	/** Call from a game tick. */
	public void update()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			clear();
			return;
		}

		final Player local = client.getLocalPlayer();
		final WorldPoint from = local == null ? null : local.getWorldLocation();
		final WorldPoint to = destination();
		if (from == null || to == null || from.getPlane() != to.getPlane())
		{
			clear();
			return;
		}

		if (from.equals(lastFrom) && to.equals(lastTo))
		{
			return;
		}
		lastFrom = from;
		lastTo = to;

		final WorldView view = client.getTopLevelWorldView();
		final CollisionData[] maps = view.getCollisionMaps();
		if (maps == null || view.getPlane() >= maps.length || maps[view.getPlane()] == null)
		{
			path = Collections.emptyList();
			return;
		}

		final LocalPoint start = LocalPoint.fromWorld(view, from);
		final LocalPoint end = LocalPoint.fromWorld(view, to);
		if (start == null || end == null)
		{
			// The target is outside the loaded scene, so there is nothing to
			// route over. The minimap edge arrow is what guides them then.
			path = Collections.emptyList();
			return;
		}

		path = PathFinder.find(
			maps[view.getPlane()].getFlags(),
			start.getSceneX(), start.getSceneY(),
			end.getSceneX(), end.getSceneY());
	}

	/**
	 * Where the player is being sent. A loaded NPC or object beats the wiki
	 * coordinate, since it is where the thing actually is right now.
	 */
	private WorldPoint destination()
	{
		final Step step = tracker.getStep();
		final Target target = step == null ? null : step.getTarget();
		if (target == null)
		{
			return null;
		}

		// On another floor there is no walkable route to the target itself, so
		// the route that matters is the one to the stairs.
		final TileObject approach = approachTracker.getApproach();
		if (approach != null)
		{
			return approach.getWorldLocation();
		}

		final List<NPC> npcs = tracker.getNpcs();
		if (!npcs.isEmpty())
		{
			return npcs.get(0).getWorldLocation();
		}
		final List<TileObject> objects = tracker.getObjects();
		if (!objects.isEmpty())
		{
			return objects.get(0).getWorldLocation();
		}

		for (List<Integer> raw : target.getPoints())
		{
			if (raw != null && raw.size() >= 3)
			{
				return new WorldPoint(raw.get(0), raw.get(1), raw.get(2));
			}
		}
		return null;
	}
}
