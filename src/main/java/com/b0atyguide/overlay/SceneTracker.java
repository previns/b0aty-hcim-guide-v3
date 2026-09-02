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
import com.b0atyguide.data.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;

/**
 * Finds the current step's target among the things actually loaded around the
 * player.
 *
 * <p>Matching is by <em>name</em>, and by ID only when the wiki confirmed one.
 * That ordering is the whole safety argument for the plugin: the guide's
 * wording is turned into a name by a grammar that is sometimes wrong, and a
 * wrong name matches nothing in the scene. A bad guess costs a missing
 * highlight, never a highlight on the wrong thing.
 */
@Singleton
public class SceneTracker
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private final List<NPC> npcs = new ArrayList<>();
	private final List<TileObject> objects = new ArrayList<>();

	private Step step;
	private String sectionLabel;
	private List<String> wantedNames = Collections.emptyList();
	private Set<Integer> wantedIds = Collections.emptySet();
	private boolean wantNpc;
	private boolean wantObject;

	/**
	 * Point the tracker at a step. Cheap enough to call on every panel
	 * selection; the scene rescan is deferred to the client thread.
	 */
	public void setStep(Step step, String sectionLabel)
	{
		this.step = step;
		this.sectionLabel = sectionLabel;

		final Target target = step == null ? null : step.getTarget();
		if (target == null || !target.isHighlightable())
		{
			// Keep the step. Most steps have nothing to outline, and the current
			// step still has to be readable on the step overlay -- forgetting it
			// here is what made the overlay blank on exactly the steps that need
			// it most.
			clearMatches();
			return;
		}

		wantedNames = target.getNames();
		wantedIds = new HashSet<>(target.getIds());
		wantNpc = Target.KIND_NPC.equals(target.getKind());
		wantObject = Target.KIND_OBJECT.equals(target.getKind());

		npcs.clear();
		objects.clear();
		clientThread.invokeLater(this::rescan);
	}

	public void clear()
	{
		step = null;
		sectionLabel = null;
		clearMatches();
	}

	/** Forget what we were looking for, but not which step we are on. */
	private void clearMatches()
	{
		wantedNames = Collections.emptyList();
		wantedIds = Collections.emptySet();
		wantNpc = false;
		wantObject = false;
		npcs.clear();
		objects.clear();
	}

	public boolean isTracking()
	{
		return !wantedNames.isEmpty();
	}

	public Step getStep()
	{
		return step;
	}

	/** "Bank 12", for the step overlay's heading. Null before a step is set. */
	public String getSectionLabel()
	{
		return sectionLabel;
	}

	public List<NPC> getNpcs()
	{
		return npcs;
	}

	public List<TileObject> getObjects()
	{
		return objects;
	}

	// --- matching ----------------------------------------------------------

	private boolean nameMatches(String candidate)
	{
		if (candidate == null)
		{
			return false;
		}
		// The wiki's page title and the game's name differ in case and in
		// non-breaking spaces often enough to matter.
		final String normalised = normalise(candidate);
		for (String wanted : wantedNames)
		{
			if (wanted != null && normalise(wanted).equals(normalised))
			{
				return true;
			}
		}
		return false;
	}

	private static String normalise(String value)
	{
		return value.replace(' ', ' ').trim().toLowerCase();
	}

	private boolean npcMatches(NPC npc)
	{
		if (!wantNpc)
		{
			return false;
		}
		if (!wantedIds.isEmpty() && wantedIds.contains(npc.getId()))
		{
			return true;
		}
		return nameMatches(npc.getName());
	}

	private boolean objectMatches(TileObject object)
	{
		if (!wantObject)
		{
			return false;
		}
		if (!wantedIds.isEmpty() && wantedIds.contains(object.getId()))
		{
			return true;
		}
		// definitionOf resolves multi-state scenery through its impostor, and
		// must run on the client thread -- which every caller here already does.
		final ObjectComposition composition = SceneObjects.definitionOf(client, object);
		return composition != null
			&& (wantedIds.contains(composition.getId()) || nameMatches(composition.getName()));
	}

	// --- scene scanning ----------------------------------------------------

	private void rescan()
	{
		npcs.clear();
		objects.clear();

		if (wantedNames.isEmpty() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (wantNpc)
		{
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (npc != null && npcMatches(npc))
				{
					npcs.add(npc);
				}
			}
		}

		if (wantObject)
		{
			SceneObjects.forEach(client, this::consider);
		}
	}

	private void consider(TileObject object)
	{
		if (object != null && objects.size() < 64 && objectMatches(object))
		{
			objects.add(object);
		}
	}

	// --- events ------------------------------------------------------------

	public void onNpcSpawned(NpcSpawned event)
	{
		if (wantNpc && npcMatches(event.getNpc()))
		{
			npcs.add(event.getNpc());
		}
	}

	public void onNpcDespawned(NpcDespawned event)
	{
		npcs.remove(event.getNpc());
	}

	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		consider(event.getGameObject());
	}

	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		objects.remove(event.getGameObject());
	}

	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		consider(event.getWallObject());
	}

	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		consider(event.getGroundObject());
	}

	public void onGameStateChanged(GameStateChanged event)
	{
		// The scene is rebuilt on every loading screen and hop, so anything we
		// were holding is stale.
		if (event.getGameState() == GameState.LOADING
			|| event.getGameState() == GameState.HOPPING
			|| event.getGameState() == GameState.LOGIN_SCREEN)
		{
			npcs.clear();
			objects.clear();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::rescan);
		}
	}
}
