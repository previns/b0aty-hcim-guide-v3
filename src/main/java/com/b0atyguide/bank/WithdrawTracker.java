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
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import com.b0atyguide.progress.HeldItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
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

	/**
	 * The last reading of what the player is carrying.
	 *
	 * <p>Volatile: written on the client thread, read from the event thread.
	 * Never null, so a caller before the first reading sees an empty inventory
	 * rather than an exception.
	 */
	private volatile Map<Integer, Integer> carried = Collections.emptyMap();

	/** Names the guide asks for that carry no ids. */
	private final List<String> unidentified = new ArrayList<>();

	/** Items the quest on this step asks for and the player is not carrying. */
	private final List<String> questItems = new ArrayList<>();
	private final Set<Integer> questIds = new LinkedHashSet<>();
	private final SectionIndex sections = new SectionIndex();

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
		return missingIds.contains(itemId) || questIds.contains(itemId);
	}

	/**
	 * Every id a quest named in this bank still wants.
	 *
	 * <p>Kept apart from the bank's own list so the panel can go on saying
	 * which came from where, while the ring in the bank treats them alike --
	 * from the player's side they are both "things to take out before leaving".
	 */
	public Set<Integer> getQuestIds()
	{
		return Collections.unmodifiableSet(questIds);
	}

	/** Whether this item is the one the current step says to travel with. */
	public boolean isTeleport(int itemId)
	{
		return teleportIds.contains(itemId);
	}

	public void clear()
	{
		clearRequirements();
		sections.clear();
		carried = Collections.emptyMap();
	}

	private void clearRequirements()
	{
		missing.clear();
		missingIds.clear();
		teleportIds.clear();
		// These two are shown on the panel beside the bank list, and they were
		// not cleared here -- so an early return in recompute left the previous
		// bank's unidentified items and quest items on screen.
		unidentified.clear();
		questItems.clear();
		questIds.clear();
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
		clearRequirements();
		// Before the early returns. The snapshot is read by callers that do not
		// care whether there is a step to bank for, and a stale reading is a
		// worse answer than an empty bank list.
		carried = carriedCounts();
		if (guide == null || current == null)
		{
			return;
		}

		if (current.getTeleport() != null)
		{
			teleportIds.addAll(current.getTeleport().getIds());
		}

		final Section section = sections.sectionOf(guide, current);
		if (section == null)
		{
			return;
		}
		sectionId = section.getId();

		for (Step step : section.getSteps())
		{
			// Only the trips to the bank. A section names plenty of items it
			// never asks you to withdraw -- logs to light, a bucket to pick up
			// on the way, cabbages to pull from a field -- and ringing those
			// made the bank list wrong at every bank in the guide.
			if (!step.isWithdraw())
			{
				continue;
			}

			// And only while it is still owed. `current` is the first step not
			// yet ticked, so anything before it is done -- and a research
			// package handed to Aubury ten steps ago must stop being called
			// missing, or the bank keeps asking for something already spent.
			if (step.getOrdinal() < current.getOrdinal())
			{
				continue;
			}

			// What the quest itself says to bring, on a step that names one. The
			// guide's withdraw line is what this bank asks for; Quest Helper
			// knows what the quest needs, and a player who reads only the
			// withdraw line arrives at the quest without it.
			final QuestHelperSteps quest = guide.questHelperFor(step);
			if (quest != null)
			{
				for (QuestHelperSteps.Wanted wanted : quest.getItems())
				{
					if (carried.getOrDefault(wanted.getId(), 0) >= 1)
					{
						continue;
					}
					// Ringed in the bank, not only listed on the panel. A bank
					// step that names a quest is telling the player to leave
					// with what that quest needs, and reading a list of names
					// off a panel while hunting a bank tab for them is the part
					// the plugin is supposed to be doing.
					questIds.add(wanted.getId());
					if (!questItems.contains(wanted.getName()))
					{
						// Once each. Several withdraw steps in a bank can name
						// the same quest, and the panel listed its items again
						// for every one of them.
						questItems.add(wanted.getName());
					}
				}
			}

			for (ItemRef item : step.getItems())
			{
				if (!item.isResolved())
				{
					// Not evidence of anything, so it is never called missing --
					// that would flag "Combat gear" for ever. But it is worth
					// saying out loud: an item the plugin cannot identify rings
					// nothing, and silence looks exactly like a bug.
					unidentified.add(item.getName());
					continue;
				}
				if (HeldItems.heldCount(item, carried) < item.getCount())
				{
					missing.add(item);
					missingIds.addAll(item.getIds());
				}
			}
		}
	}

	/**
	 * Items the quest named on this step wants, that are not carried.
	 *
	 * <p>Shown beside the bank's own list. Quest Helper knows what a quest
	 * needs; the guide's withdraw line only knows what this bank asks for.
	 */
	public List<String> getQuestItems()
	{
		return Collections.unmodifiableList(questItems);
	}

	/**
	 * Names this bank asks for that the plugin could not identify.
	 *
	 * <p>Shown so a player knows why nothing lit up. Mostly category words the
	 * guide uses on purpose -- "Combat gear", "Potions" -- but a genuine gap
	 * looks identical from the outside, and a silent one is indistinguishable
	 * from the plugin being broken.
	 */
	public List<String> getUnidentified()
	{
		return Collections.unmodifiableList(unidentified);
	}

	/** The bank whose list this reflects, for the panel heading. */
	public String getSectionId()
	{
		return sectionId;
	}

	/**
	 * What was carried when the inventory was last read.
	 *
	 * <p>A snapshot, deliberately. Reading an item container asserts the client
	 * thread, and this is asked from the panel and from {@code startUp()},
	 * which are on the event thread -- calling through would throw there and
	 * RuneLite would disable the plugin on the spot. That has now happened
	 * twice, which is why every item-container read here is deferred.
	 *
	 * <p>Refreshed by {@link #update} on every inventory change and every step
	 * change, both of which arrive on the client thread, so it is never more
	 * than a tick behind what the player is holding.
	 */
	public Map<Integer, Integer> carried()
	{
		return carried;
	}

	/**
	 * What is carried, and how many of each.
	 *
	 * <p>Counted rather than merely listed. A set answers "do you have an arrow
	 * shaft", which is the wrong question for a step asking for 454 of them --
	 * one shaft turned the ring off and the player walked away with one.
	 */
	private Map<Integer, Integer> carriedCounts()
	{
		final Map<Integer, Integer> counts = new HashMap<>();
		addAll(counts, client.getItemContainer(InventoryID.INV));
		// Worn items count: the guide equips things as it goes, and a step
		// asking for leather boots is satisfied by wearing them.
		addAll(counts, client.getItemContainer(InventoryID.WORN));
		return counts;
	}

	private static void addAll(Map<Integer, Integer> counts, ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() >= 0)
			{
				// Not "> 0". An empty slot is -1; zero is Dwarf remains, the
				// first item in the game, and excluding it meant nobody could
				// ever be holding one. Quest Helper's Dwarf Cannon branches on
				// exactly that item, so the guide kept saying "get the dwarf
				// remains at the top of the tower" to a player carrying them.
				// Worn equipment reports a quantity of zero, so it counts as one.
				counts.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
			}
		}
	}

	/**
	 * Inventory changes should scan one bank's requirements, not locate that bank
	 * by walking the whole guide again. Guide replacement invalidates the index;
	 * ID matching preserves the old lookup even when a caller has a copied Step.
	 */
	static final class SectionIndex
	{
		private Guide indexedGuide;
		private final Map<String, Section> byStepId = new HashMap<>();

		Section sectionOf(Guide guide, Step target)
		{
			if (indexedGuide != guide)
			{
				clear();
				indexedGuide = guide;
				if (guide != null)
				{
					for (Section section : guide.getSections())
					{
						for (Step step : section.getSteps())
						{
							byStepId.putIfAbsent(step.getId(), section);
						}
					}
				}
			}
			return target == null ? null : byStepId.get(target.getId());
		}

		void clear()
		{
			indexedGuide = null;
			byStepId.clear();
		}
	}
}
