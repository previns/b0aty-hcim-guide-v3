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

import com.b0atyguide.data.Step;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Everything nearby that can be banked at, while the step is a trip to a bank.
 *
 * <p>"Bank at Draynor" resolves to Draynor the place, so the highlight had
 * nothing to draw on -- a coordinate, not a booth. What the player wants is
 * whichever booth, chest or banker is closest, and there is usually more than
 * one.
 *
 * <p>Found by the game's own <b>actions</b>: anything offering "Bank" or
 * "Use-bank" counts, whatever it is called. Booths, chests, bankers, deposit
 * boxes and the odd oddity all answer to that, and a list of names would miss
 * whichever one nobody thought of.
 */
@Singleton
public class BankTracker
{
	/** Far enough to cover a bank across a room, short of the next building. */
	private static final int MAX_TILES = 20;

	/** Plenty for any real bank; a guard against drawing over the whole screen. */
	private static final int MAX_MATCHES = 24;

	@Inject
	private Client client;

	private final List<TileObject> objects = new ArrayList<>();
	private final List<NPC> npcs = new ArrayList<>();

	/** What the held lists were scanned for; null means they are not valid. */
	private String lastStepId;
	private WorldPoint lastScanFrom;

	public List<TileObject> getObjects()
	{
		return Collections.unmodifiableList(objects);
	}

	public List<NPC> getNpcs()
	{
		return Collections.unmodifiableList(npcs);
	}

	public boolean isTracking()
	{
		return !objects.isEmpty() || !npcs.isEmpty();
	}

	public void clear()
	{
		objects.clear();
		npcs.clear();
	}

	/** The scene is rebuilt on a loading screen, so anything held is stale. */
	public void onSceneChanged()
	{
		clear();
		lastStepId = null;
		lastScanFrom = null;
	}

	/**
	 * Call from a game tick.
	 *
	 * <p>Scanning the scene means ~10,800 tiles and a composition lookup per
	 * object on each, which is far too much to repeat every tick for something
	 * that does not move. The results only go stale when the step changes, the
	 * player walks, or the scene is rebuilt, so the scan is limited to those.
	 */
	public void update(Step current)
	{
		if (current == null || !current.isBanking()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			clear();
			lastStepId = null;
			return;
		}

		final WorldPoint here =
			client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
		if (here == null)
		{
			clear();
			lastStepId = null;
			return;
		}

		if (current.getId().equals(lastStepId) && here.equals(lastScanFrom))
		{
			return;
		}
		lastStepId = current.getId();
		lastScanFrom = here;

		clear();
		final WorldView view = client.getTopLevelWorldView();

		for (NPC npc : view.npcs())
		{
			if (npcs.size() >= MAX_MATCHES)
			{
				break;
			}
			if (npc != null && npc.getWorldLocation().distanceTo(here) <= MAX_TILES
				&& banks(npc.getTransformedComposition()))
			{
				npcs.add(npc);
			}
		}

		SceneObjects.forEach(client, object -> consider(object, here));
	}

	private void consider(TileObject object, WorldPoint here)
	{
		if (object == null || objects.size() >= MAX_MATCHES
			|| object.getWorldLocation().distanceTo(here) > MAX_TILES)
		{
			return;
		}
		final ObjectComposition composition = SceneObjects.definitionOf(client, object);
		if (composition == null)
		{
			return;
		}
		if (banks(composition.getActions()) && !objects.contains(object))
		{
			objects.add(object);
		}
	}

	private static boolean banks(NPCComposition composition)
	{
		return composition != null && banks(composition.getActions());
	}

	/**
	 * Whether the game offers banking here.
	 *
	 * <p>"Bank" covers booths and bankers, "Use-bank" the chests. Deliberately
	 * not "Deposit": a deposit box is not somewhere the guide's withdraw lists
	 * can be filled, and offering one as a bank would send the player to the
	 * wrong thing.
	 */
	private static boolean banks(String[] actions)
	{
		return SceneObjects.hasAction(actions, "bank", null)
			|| SceneObjects.hasAction(actions, "use-bank", null);
	}
}
