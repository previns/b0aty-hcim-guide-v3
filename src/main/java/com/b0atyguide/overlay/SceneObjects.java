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

import java.util.Locale;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;

/**
 * Walking the loaded scene, and asking the game what an object can do.
 *
 * <p>Four places were each hand-rolling the same loop and each remembering a
 * different subset of the four kinds of tile object -- game, wall, decorative,
 * ground. Forgetting one is a silent miss: a wall-mounted bank booth or a
 * ground-level ladder simply never matches, with nothing to indicate why.
 */
final class SceneObjects
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
		final Tile[][][] tiles = view.getScene().getTiles();
		final int plane = view.getPlane();
		if (plane < 0 || plane >= tiles.length)
		{
			return;
		}

		for (Tile[] row : tiles[plane])
		{
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
						visitor.accept(object);
					}
				}
				accept(visitor, tile.getWallObject());
				accept(visitor, tile.getDecorativeObject());
				accept(visitor, tile.getGroundObject());
			}
		}
	}

	private static void accept(Consumer<TileObject> visitor, TileObject object)
	{
		if (object != null)
		{
			visitor.accept(object);
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
	static ObjectComposition definitionOf(Client client, TileObject object)
	{
		final ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition == null)
		{
			return null;
		}
		if (composition.getImpostorIds() != null && composition.getImpostor() != null)
		{
			return composition.getImpostor();
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
	static boolean hasAction(String[] actions, String verb, String qualifier)
	{
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action == null)
			{
				continue;
			}
			final String lowered = action.toLowerCase(Locale.ROOT);
			if (lowered.startsWith(verb) && (qualifier == null || lowered.contains(qualifier)))
			{
				return true;
			}
		}
		return false;
	}
}
