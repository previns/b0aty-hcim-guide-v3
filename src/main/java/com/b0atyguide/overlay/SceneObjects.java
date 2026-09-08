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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Walking the loaded scene, and asking the game what an object can do.
 *
 * <p>Four places were each hand-rolling the same loop and each remembering a
 * different subset of the four kinds of tile object -- game, wall, decorative,
 * ground. Forgetting one is a silent miss: a wall-mounted bank booth or a
 * ground-level ladder simply never matches, with nothing to indicate why.
 */
public final class SceneObjects
{
	private SceneObjects()
	{
	}

	/**
	 * Hand every object on the player's current plane to {@code visitor}.
	 *
	 * <p>The whole scene is ~10,800 tiles with up to four objects each, so this
	 * is far too heavy to run per frame. Callers scan on a change and cache.
	 */
	static void forEach(Client client, Consumer<TileObject> visitor)
	{
		final WorldView view = client.getTopLevelWorldView();
		if (view == null || view.getScene() == null)
		{
			return;
		}
		final Tile[][][] tiles = view.getScene().getTiles();
		final int plane = view.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
		{
			return;
		}

		// A game object appears on every tile of its footprint. Matching those
		// entries separately made the 3x3 cannon appear nine times in the target
		// list and drew the same expensive model outline nine times per frame.
		// Identity matters: two distinct objects with one id are still two
		// targets, while one object keeps its real footprint for pathfinding.
		final UniqueVisitor<TileObject> unique = new UniqueVisitor<>(visitor);
		for (Tile[] row : tiles[plane])
		{
			if (row == null)
			{
				continue;
			}
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}
				for (TileObject object : tile.getGameObjects())
				{
					if (object != null)
					{
						unique.accept(object);
					}
				}
				unique.accept(tile.getWallObject());
				unique.accept(tile.getDecorativeObject());
				unique.accept(tile.getGroundObject());
			}
		}
	}

	/** Scan-local identity filtering, testable without inventing a game client. */
	static final class UniqueVisitor<T> implements Consumer<T>
	{
		private final Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		private final Consumer<T> visitor;

		UniqueVisitor(Consumer<T> visitor)
		{
			this.visitor = visitor;
		}

		@Override
		public void accept(T object)
		{
			if (object != null && seen.add(object))
			{
				visitor.accept(object);
			}
		}
	}

	/**
	 * The definition the game would actually use for this object.
	 *
	 * <p>Multi-state scenery -- doors, banks, anything that changes with a
	 * varbit -- reports its real name and actions through an impostor, and the
	 * raw id belongs to a placeholder that matches nothing.
	 *
	 * <p>Must be called on the client thread.
	 */
	public static ObjectComposition definitionOf(Client client, TileObject object)
	{
		final ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition == null)
		{
			return null;
		}
		if (composition.getImpostorIds() != null)
		{
			final ObjectComposition impostor = composition.getImpostor();
			if (impostor != null)
			{
				return impostor;
			}
		}
		return composition;
	}

	/**
	 * Whether the game offers an action starting with {@code verb} and
	 * containing {@code qualifier}, ignoring case.
	 *
	 * <p>Asking the game what something does beats a list of names every time:
	 * a name list silently misses whatever nobody thought of, and there are ten
	 * near-identical constants for a staircase alone.
	 */
	public static boolean hasAction(String[] actions, String verb, String qualifier)
	{
		if (actions == null || verb == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action == null)
			{
				continue;
			}
			if (action.regionMatches(true, 0, verb, 0, verb.length())
				&& (qualifier == null || containsIgnoringCase(action, qualifier)))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsIgnoringCase(String value, String part)
	{
		for (int i = 0; i <= value.length() - part.length(); i++)
		{
			if (value.regionMatches(true, i, part, 0, part.length()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * A single instruction evaluation's scene lookup. Indexing by both the
	 * spawned and transformed id answers many branches with one scene scan;
	 * keeping this beyond that evaluation would hide varbit-driven changes.
	 */
	static final class PresenceIndex
	{
		private final Map<Integer, List<WorldPoint>> byId = new HashMap<>();

		void add(int id, WorldPoint point)
		{
			byId.computeIfAbsent(id, ignored -> new ArrayList<>()).add(point);
		}

		boolean anyOf(List<Integer> ids, List<List<Integer>> zones)
		{
			for (Integer id : ids)
			{
				final List<WorldPoint> points = byId.get(id);
				if (points == null)
				{
					continue;
				}
				for (WorldPoint point : points)
				{
					if (inside(zones, point))
					{
						return true;
					}
				}
			}
			return false;
		}
	}

	/** The same inclusive zone semantics for NPCs, objects and ground items. */
	static boolean inside(List<List<Integer>> zones, WorldPoint point)
	{
		if (zones == null || zones.isEmpty())
		{
			return true;
		}
		if (point == null)
		{
			return false;
		}
		for (List<Integer> box : zones)
		{
			if (box != null && box.size() >= 6
				&& box.get(0) <= point.getX() && point.getX() <= box.get(3)
				&& box.get(1) <= point.getY() && point.getY() <= box.get(4)
				&& box.get(2) <= point.getPlane() && point.getPlane() <= box.get(5))
			{
				return true;
			}
		}
		return false;
	}
}
