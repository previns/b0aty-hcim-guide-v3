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

import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.QuestHelperSteps;
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
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;

/**
 * Tiles holding an item the current step tells you to pick up.
 *
 * <p>Follows Quest Helper's model, which is proven: keep a set of tiles, add to
 * it when a matching item spawns, drop from it when one despawns, and rescan
 * the loaded scene whenever the step or the region changes. Scanning every
 * frame would walk 104x104 tiles for something that changes rarely.
 *
 * <p>Matching is by id, never by name: the step's items come from the pipeline
 * already resolved, so "take the tinderbox" knows which tinderbox. A step whose
 * items did not resolve highlights nothing rather than guessing.
 */
@Singleton
public class GroundItemTracker
{
	/** Tiles, in world coordinates, currently holding something wanted. */
	private final Set<WorldPoint> tiles = new HashSet<>();
	private List<WorldPoint> tileSnapshot;

	private final Client client;

	/** Ids the current step asks for; empty when it asks for nothing. */
	private Set<Integer> wanted = new HashSet<>();

	@Inject
	GroundItemTracker(Client client)
	{
		this.client = client;
	}

	/**
	 * Point the tracker at a step.
	 *
	 * <p>Only steps the build marked as acquiring something: "Kill a Chicken.
	 * Take Everything" wants its drops highlighted, while a step that merely
	 * mentions an item does not.
	 */
	public void setStep(Step step)
	{
		setStep(step, null);
	}

	/**
	 * The step, and what Quest Helper is asking for right now.
	 *
	 * <p>Its items belong here as much as the guide's own: "Pick up the Child's
	 * blanket in the room to the south" is a Quest Helper step whose whole
	 * subject is an item on the floor, and reading only the guide's list left
	 * the blanket unmarked in a room the player was standing in.
	 */
	public void setStep(Step step, QuestHelperSteps.Instruction instruction)
	{
		final Set<Integer> ids = new HashSet<>();
		if (instruction != null)
		{
			for (QuestHelperSteps.Need need : instruction.getItems())
			{
				ids.addAll(need.getIds());
			}
		}
		if (step != null)
		{
			// A step told to acquire things wants its whole list watched --
			// "Kill a Chicken. Take Everything" covers the bones and feather.
			if (step.isAcquires())
			{
				for (ItemRef item : step.getItems())
				{
					ids.addAll(item.getIds());
				}
			}

			// And a step whose target is itself an item: "Loot the lit candle",
			// "Collect Cheese". Those name one thing rather than a list, and
			// without this they highlighted nothing at all -- the renderer only
			// ever outlined NPCs and scenery.
			final Target target = step.getTarget();
			if (target != null && Target.KIND_ITEM.equals(target.getKind()))
			{
				ids.addAll(target.getIds());
			}
		}
		wanted = ids;
		rescan();
	}

	/**
	 * Whether any of these items is on the floor, inside the box if given.
	 *
	 * <p>For a quest's branch conditions, which ask it to notice that something
	 * has been dropped or has not been picked up yet. Its own scan rather than
	 * the tracked set: the tracked set is what the current step wants, and a
	 * condition asks about something else entirely.
	 */
	public boolean anyOf(java.util.List<Integer> ids, java.util.List<java.util.List<Integer>> zone)
	{
		if (ids == null || ids.isEmpty() || client.getTopLevelWorldView() == null)
		{
			return false;
		}
		final Tile[][][] scene = client.getTopLevelWorldView().getScene().getTiles();
		final int plane = client.getTopLevelWorldView().getPlane();
		if (scene == null || plane >= scene.length || scene[plane] == null)
		{
			return false;
		}
		for (Tile[] column : scene[plane])
		{
			if (column == null)
			{
				continue;
			}
			for (Tile tile : column)
			{
				if (tile == null || tile.getGroundItems() == null)
				{
					continue;
				}
				for (TileItem item : tile.getGroundItems())
				{
					if (item != null && ids.contains(item.getId()))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Tiles worth drawing, in world coordinates. */
	public List<WorldPoint> getTiles()
	{
		if (tileSnapshot == null)
		{
			tileSnapshot = Collections.unmodifiableList(new ArrayList<>(tiles));
		}
		return tileSnapshot;
	}

	public void onItemSpawned(ItemSpawned event)
	{
		final TileItem item = event.getItem();
		if (item != null && wanted.contains(item.getId()) && event.getTile() != null)
		{
			if (tiles.add(event.getTile().getWorldLocation()))
			{
				tileSnapshot = null;
			}
		}
	}

	public void onItemDespawned(ItemDespawned event)
	{
		// Only this tile changed. Check its remaining stack, not every tile in
		// the loaded scene; exclude the despawned instance even if the event is
		// delivered before the client removes it from the tile's item list.
		final TileItem item = event.getItem();
		if (item != null && wanted.contains(item.getId()))
		{
			final Tile tile = event.getTile();
			if (tile == null)
			{
				rescan();
				return;
			}
			if (tile.getGroundItems() != null)
			{
				for (TileItem remaining : tile.getGroundItems())
				{
					if (remaining != null && remaining != item && wanted.contains(remaining.getId()))
					{
						return;
					}
				}
			}
			if (tiles.remove(tile.getWorldLocation()))
			{
				tileSnapshot = null;
			}
		}
	}

	/** Called when the scene is rebuilt, so every cached tile is stale. */
	public void onSceneChanged()
	{
		rescan();
	}

	public void clear()
	{
		wanted = new HashSet<>();
		tiles.clear();
		tileSnapshot = null;
	}

	/**
	 * Walk the loaded scene for wanted items.
	 *
	 * <p>Needs the client thread. Cheap enough at a step change or a region
	 * load, which is the only time it runs.
	 */
	private void rescan()
	{
		tiles.clear();
		tileSnapshot = null;
		if (wanted.isEmpty() || client.getTopLevelWorldView() == null)
		{
			return;
		}

		final Tile[][][] scene = client.getTopLevelWorldView().getScene().getTiles();
		final int plane = client.getTopLevelWorldView().getPlane();
		if (scene == null || plane >= scene.length || scene[plane] == null)
		{
			return;
		}

		for (Tile[] column : scene[plane])
		{
			if (column == null)
			{
				continue;
			}
			for (Tile tile : column)
			{
				if (tile == null)
				{
					continue;
				}
				final List<TileItem> items = tile.getGroundItems();
				if (items == null)
				{
					continue;
				}
				for (TileItem item : items)
				{
					if (item != null && wanted.contains(item.getId()))
					{
						tiles.add(tile.getWorldLocation());
						break;
					}
				}
			}
		}
	}
}
