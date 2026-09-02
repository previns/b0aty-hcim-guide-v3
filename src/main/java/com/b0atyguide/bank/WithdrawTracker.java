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
package com.b0atyguide.bank;

import com.b0atyguide.data.Guide;
import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;

/**
 * Which of the current bank's withdraw list the player is not carrying.
 *
 * <p>The guide's withdraw steps are the part that punishes you later: leave the
 * bank without a tinderbox and you find out twenty steps away. Comparing the
 * list against the inventory and worn equipment is exact -- these are ids, and
 * the answer is a fact rather than a guess.
 *
 * <p>A requirement is satisfied by <em>any</em> of its ids. "Pickaxe" carries
 * every pickaxe, so whichever one is in the bag counts.
 */
@Singleton
public class WithdrawTracker
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private final List<ItemRef> missing = new ArrayList<>();
	private final Set<Integer> missingIds = new LinkedHashSet<>();

	/**
	 * The item the current step travels with, when it names one.
	 *
	 * <p>Kept apart from the missing list: this is not something to fetch from
	 * the bank, it is the thing to click now, and it is highlighted whether or
	 * not the player already has it.
	 */
	private final Set<Integer> teleportIds = new LinkedHashSet<>();
	private String sectionId;

	/** Requirements from the current bank that are not carried. */
	public List<ItemRef> getMissing()
	{
		return Collections.unmodifiableList(missing);
	}

	/** Every id that would satisfy something still missing. */
	public Set<Integer> getMissingIds()
	{
		return Collections.unmodifiableSet(missingIds);
	}

	public boolean isMissing(int itemId)
	{
		return missingIds.contains(itemId);
	}

	/** Whether this item is the one the current step says to travel with. */
	public boolean isTeleport(int itemId)
	{
		return teleportIds.contains(itemId);
	}

	public void clear()
	{
		missing.clear();
		missingIds.clear();
		teleportIds.clear();
		sectionId = null;
	}

	/**
	 * Recompute against the bank the current step belongs to.
	 *
	 * <p>Always deferred to the client thread. Reading an item container off it
	 * throws "must be called on client thread", and callers arrive from both
	 * sides -- startUp() and the panel run on the event thread, the container
	 * event runs on the client thread. Deferring is the only version that is
	 * right for all of them, and a tick of latency is invisible.
	 *
	 * <p>Everything that reads the result also runs on the client thread, so
	 * the lists are only ever touched from one place.
	 */
	public void update(Guide guide, Step current)
	{
		clientThread.invokeLater(() -> recompute(guide, current));
	}

	private void recompute(Guide guide, Step current)
	{
		clear();
		if (guide == null || current == null)
		{
			return;
		}

		if (current.getTeleport() != null)
		{
			teleportIds.addAll(current.getTeleport().getIds());
		}

		final Section section = sectionOf(guide, current);
		if (section == null)
		{
			return;
		}
		sectionId = section.getId();

		final Set<Integer> carried = carriedIds();
		for (Step step : section.getSteps())
		{
			for (ItemRef item : step.getItems())
			{
				if (!item.isResolved())
				{
					// Unresolved names are not evidence of anything. Reporting
					// them as missing would flag "Combat gear" forever.
					continue;
				}
				if (Collections.disjoint(item.getIds(), carried))
				{
					missing.add(item);
					missingIds.addAll(item.getIds());
				}
			}
		}
	}

	/** The bank whose list this reflects, for the panel heading. */
	public String getSectionId()
	{
		return sectionId;
	}

	private Set<Integer> carriedIds()
	{
		final Set<Integer> ids = new LinkedHashSet<>();
		addAll(ids, client.getItemContainer(InventoryID.INV));
		// Worn items count: the guide equips things as it goes, and a step
		// asking for leather boots is satisfied by wearing them.
		addAll(ids, client.getItemContainer(InventoryID.WORN));
		return ids;
	}

	private static void addAll(Set<Integer> ids, ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() > 0)
			{
				ids.add(item.getId());
			}
		}
	}

	private static Section sectionOf(Guide guide, Step target)
	{
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getId().equals(target.getId()))
				{
					return section;
				}
			}
		}
		return null;
	}
}
