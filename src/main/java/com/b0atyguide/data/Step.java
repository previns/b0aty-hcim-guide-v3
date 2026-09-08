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
package com.b0atyguide.data;

import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * One line of the guide, and everything the pipeline could resolve about it.
 *
 * <p>{@link #getText()} is verbatim from the wiki and is the only field that
 * always exists. The rest are optional by design: most steps name no target, no
 * items and no destination, and an absent field means "not known", never "none".
 */
public class Step
{
	private String id;
	private int ordinal;
	private int depth;
	private String kind;
	private String text;
	private Integer inventorySlots;
	private Target target;
	private List<Seller> sellers;

	/**
	 * Everyone who sells what this step says to buy.
	 *
	 * <p>The guide names a shop's goods and trusts the player to find the
	 * counter -- "Buy 2x Bronze Med Helm in Barbarian Village" never says Peksa,
	 * because you are standing in the village and there is one shop. So the
	 * build offers every shop that stocks the item and the plugin marks the one
	 * nearest the player, which is Peksa there and somebody else in Varrock
	 * without either being written down.
	 *
	 * <p>Empty on a step that names its seller outright; that one is the target.
	 */
	public List<Seller> getSellers()
	{
		return sellers == null ? Collections.emptyList() : sellers;
	}

	/** One shopkeeper, with the ids to outline and where the wiki puts them. */
	public static class Seller
	{
		private String name;
		private List<Integer> ids;
		private List<List<Integer>> points;

		public String getName()
		{
			return name == null ? "" : name;
		}

		public List<Integer> getIds()
		{
			return ids == null ? Collections.emptyList() : ids;
		}

		public List<List<Integer>> getPoints()
		{
			return points == null ? Collections.emptyList() : points;
		}
	}
	private Completion completion;
	private Approach approach;
	private String questHelper;
	private String diaryTask;
	private boolean questStep;
	private String questContext;
	private boolean advice;

	/**
	 * Whether the step is telling the player something rather than asking.
	 *
	 * <p>"MAX HITS: Ahrim 20, Dharok 29", "Turn NPC 'Attack' Options to 'Always
	 * Right-click'", "You can get multiple Garlic by spamclicking the cupboard".
	 * The guide is full of them, and from the outside they are indistinguishable
	 * from a step whose extraction failed -- which made the count of steps with
	 * no guidance meaningless as a measure of anything.
	 *
	 * <p>132 steps. Labelling them is what lets the silence of the other 425
	 * mean something.
	 */
	public boolean isAdvice()
	{
		return advice;
	}

	/**
	 * The quest this step is being done <em>inside</em>, or null.
	 *
	 * <p>The guide nests the errands you do on the way: "Take 1 extra Rotten
	 * Apple" under "Start Biohazard until you get the samples". A player picking
	 * up the apple is still running Biohazard, and the plugin went quiet as soon
	 * as they stepped onto it -- the apple's own bracket names a quest a hundred
	 * banks away, which is only the reason for the pickup.
	 *
	 * <p>Governs what is shown, never what is ticked. The apple is picked up by
	 * hand, and Biohazard moving on says nothing about it.
	 */
	public String getQuestContext()
	{
		return questContext;
	}
	private Integer questDoneAt;
	private Integer questDoneAtPanel;
	private boolean questCompletes;
	private List<QuestHelperSteps.Need> questStopItems;
	private boolean questStopUnresolved;
	private boolean questFollow;
	private boolean questStartOnly;
	private Integer questStopValue;
	private QuestHelperSteps.Requirement questStopCondition;

	public QuestHelperSteps.Requirement getQuestStopCondition()
	{
		return questStopCondition;
	}

	/** Source-verified quest state, unlike a boundary inferred from a later visit. */
	public Integer getQuestStopValue()
	{
		return questStopValue;
	}

	/** A macro quest instruction, not a single named NPC/object errand. */
	public boolean isQuestFollow()
	{
		return questFollow;
	}

	/** The guide asks only to start; the mapped first progress value proves it. */
	public boolean isQuestStartOnly()
	{
		return questStartOnly;
	}

	/** An explicit guide milestone, resolved against this quest's item IDs. */
	public List<QuestHelperSteps.Need> getQuestStopItems()
	{
		return questStopItems == null ? Collections.emptyList() : questStopItems;
	}

	/** A stated goal which must not be guessed from a coarse progress change. */
	public boolean isQuestStopUnresolved()
	{
		return questStopUnresolved;
	}

	/**
	 * The step of Quest Helper's own list at which this guide step hands over.
	 *
	 * <p>For the shape the guide uses constantly: run a quest up to a stated
	 * point, go and do something else, come back and finish it. The quest's
	 * progress value usually cannot express that point -- "continue Sheep
	 * Herder until all 4 Sheep bones are burnt" happens entirely inside one
	 * value -- but Quest Helper's sidebar order can, and the step after this
	 * one is the last entry in it.
	 */
	public Integer getQuestDoneAtPanel()
	{
		return questDoneAtPanel;
	}

	/**
	 * The quest progress value that finishes this step, or null.
	 *
	 * <p>What a step is worth is the distance to the next one. "Head North East
	 * &amp; continue Gertrude's Cat" is four of Quest Helper's steps -- the
	 * ladder, the milk, the sardine, the kitten -- and ticking it the moment the
	 * quest moved at all dropped the player onto the next guide step with three
	 * still to do.
	 *
	 * <p>Derived at build time by placing each step on the quest's own timeline
	 * through the ids it names, so this is where the <em>following</em> step
	 * begins. Null where the following step named nothing placeable.
	 */
	public Integer getQuestDoneAt()
	{
		return questDoneAt;
	}

	/**
	 * Whether this is the guide's last step for its quest.
	 *
	 * <p>Done when the quest is past everything Quest Helper describes, which is
	 * what {@code at()} returning null already means.
	 */
	public boolean isQuestCompletes()
	{
		return questCompletes;
	}
	private boolean acquires;
	private boolean withdraw;
	private List<Integer> arrivesAt;
	private boolean depositsAll;
	private boolean banking;
	private Teleport teleport;
	private Spell spell;
	private Destination destination;
	private List<ItemRef> items;
	private List<Tag> tags;
	private List<List<Integer>> dialogue;
	private List<String> videoIds;
	private List<String> urls;

	public String getId()
	{
		return id;
	}

	public int getOrdinal()
	{
		return ordinal;
	}

	/** 1 for a top-level step, 2 or 3 for the guide's nested sub-steps. */
	public int getDepth()
	{
		return depth < 1 ? 1 : depth;
	}

	public String getKind()
	{
		return kind;
	}

	public String getText()
	{
		return text == null ? "" : text;
	}

	public Integer getInventorySlots()
	{
		return inventorySlots;
	}

	public Target getTarget()
	{
		return target;
	}

	/** The spellbook entry to cast, or null. */
	public Spell getSpell()
	{
		return spell;
	}

	/** How the step says to travel, or null. */
	public Teleport getTeleport()
	{
		return teleport;
	}

	/**
	 * Whether this step sends the player to a bank.
	 *
	 * <p>From the verb the guide used, not a search for the word: "Keep the
	 * Shrimps in your bank for later" mentions one without being a trip to it.
	 */
	public boolean isBanking()
	{
		return banking;
	}

	/** Whether this step empties the inventory, ending every carried assumption. */
	public boolean isDepositsAll()
	{
		return depositsAll;
	}


	/**
	 * Where the player has to be for this step to be done, or null.
	 *
	 * <p>Set only for steps that are <em>purely</em> travel. "Head to the
	 * basement and talk to Sedridor" is not one: arriving is half of it, and
	 * ticking there would skip the talk.
	 */
	public WorldPoint getArrivesAt()
	{
		return arrivesAt == null || arrivesAt.size() < 3
			? null
			: new WorldPoint(arrivesAt.get(0), arrivesAt.get(1), arrivesAt.get(2));
	}

	/**
	 * Whether this step is the trip to the bank.
	 *
	 * <p>Only these decide what the bank rings. A section mentions plenty
	 * of items it never asks you to withdraw -- logs to light, a bucket to
	 * pick up on the way, cabbages to pull from a field -- and ringing
	 * those made the list wrong at every bank in the guide.
	 */
	public boolean isWithdraw()
	{
		return withdraw;
	}

	/**
	 * Whether this step is finished by ending up holding its items.
	 *
	 * <p>Set at build time only where the step plainly says to acquire them
	 * ("Withdraw:", "Buy", "Take") <em>and</em> every item resolved to ids, so
	 * a partly-known list stays manual rather than ticking on half a match.
	 */
	public boolean isAcquires()
	{
		return acquires;
	}

	/** Key into {@link Guide#getQuestHelpers()}, or null. */
	public String getQuestHelper()
	{
		return questHelper;
	}

	/**
	 * Whether this step <em>is</em> the quest being done, not a quest mentioned.
	 *
	 * <p>The build sets it from a [Quest Name] tag, or from the guide writing
	 * "start", "continue" or "complete" immediately before the quest's name.
	 * The distinction is the whole safety of ticking on quest progress: the
	 * route mentions quests constantly in passing -- "keep 3 Bronze Bars for
	 * Tourist Trap" -- and those must never complete themselves because the
	 * quest happened to move.
	 */
	public boolean isQuestStep()
	{
		return questStep;
	}

	/**
	 * Key into {@link Guide#getDiaryTasks()}, or null.
	 *
	 * <p>"&lt;varplayer&gt;:&lt;bit&gt;" -- the same two numbers as
	 * {@link #getCompletion()}, which is how the join is exact.
	 */
	public String getDiaryTask()
	{
		return diaryTask;
	}

	/** How to reach a target on another floor, or null. */
	public Approach getApproach()
	{
		return approach;
	}

	/** A completion the client can verify, or null for the great majority. */
	public Completion getCompletion()
	{
		return completion;
	}

	public Destination getDestination()
	{
		return destination;
	}

	public List<ItemRef> getItems()
	{
		return items == null ? Collections.emptyList() : items;
	}

	public List<Tag> getTags()
	{
		return tags == null ? Collections.emptyList() : tags;
	}

	public List<List<Integer>> getDialogue()
	{
		return dialogue == null ? Collections.emptyList() : dialogue;
	}

	public List<String> getVideoIds()
	{
		return videoIds == null ? Collections.emptyList() : videoIds;
	}

	public List<String> getUrls()
	{
		return urls == null ? Collections.emptyList() : urls;
	}

	/** A quest whose completion the client can actually verify, or null. */
	public Tag getVerifiableQuest()
	{
		for (Tag tag : getTags())
		{
			if (tag.getConstant() != null)
			{
				return tag;
			}
		}
		return null;
	}
}
