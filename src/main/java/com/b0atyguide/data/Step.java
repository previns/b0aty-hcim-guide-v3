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
import java.util.ArrayList;
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

	/**
	 * Where the step's items lie on the ground, from the wiki's spawn table.
	 *
	 * <p>"Collect 2x Purple Dye when passing" never says where. The author wrote
	 * "when passing" because the spawn is on the way, but the step named no
	 * place, so the dye only outlined once the player was standing on it -- a
	 * highlight, not guidance, and 175 acquiring steps had no target at all.
	 *
	 * <p>Only for items that lie in a few places. A bucket spawns in forty and
	 * marking them answers a question nobody asked; the build drops those rather
	 * than sprinkle the map.
	 */
	public List<Spawn> getSpawns()
	{
		return spawns == null ? Collections.emptyList() : spawns;
	}

	/** One item, the ids to outline, and every tile the wiki puts it on. */
	public static class Spawn
	{
		private String name;
		private List<Integer> ids;
		private List<List<Integer>> points;
		private List<String> places;

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

		/** Readable locations, in the same order as {@link #getPoints()}. */
		public List<String> getPlaces()
		{
			return places == null ? Collections.emptyList() : places;
		}
	}

	private List<Spawn> spawns;
	private Travel travel;

	/**
	 * The vehicle a travelling step means, or null.
	 *
	 * <p>"Take the boat to Rimmington" never says Captain Barnaby. 98 of the
	 * guide's 140 travelling steps named a place and nothing else, so the plugin
	 * walked the player to a dock and went quiet.
	 *
	 * <p>Only where the step names a vehicle and the transport table has a route
	 * of that kind to that place. "Head to Catherby" is a walk, and answering it
	 * with a boat would be a different instruction.
	 */
	public Travel getTravel()
	{
		return travel;
	}

	/** A destination with independently identified departure vehicles. */
	public static class Travel
	{
		private String place;
		private String kind;
		private int id;
		private String action;
		private List<List<Integer>> origins;
		private List<Integer> destination;
		private List<Departure> departures;

		public List<Departure> getDepartures()
		{
			if (departures != null) { return departures; }
			// Compatibility with already-shipped data; new builds retain each ID.
			List<Departure> legacy = new ArrayList<>();
			if (origins != null && id > 0)
			{
				for (List<Integer> origin : origins)
				{
					Departure d = new Departure();
					d.id = id; d.origin = origin; d.action = action;
					legacy.add(d);
				}
			}
			return legacy;
		}

		public List<Integer> getIds()
		{
			List<Integer> ids = new ArrayList<>();
			for (Departure d : getDepartures()) { if (!ids.contains(d.id)) { ids.add(d.id); } }
			return ids;
		}

		public WorldPoint nearestOrigin(WorldPoint from)
		{
			if (from == null) { return null; }
			WorldPoint best = null;
			int distance = Integer.MAX_VALUE;
			for (Departure d : getDepartures())
			{
				WorldPoint point = d.point();
				if (point != null && point.getPlane() == from.getPlane()
					&& point.distanceTo2D(from) < distance)
				{
					best = point; distance = point.distanceTo2D(from);
				}
			}
			return best;
		}

		/** The source's arrival tile, not a town-centre marker. A small landing
		 * tolerance allows the first walking tick after a loading screen. */
		public boolean atArrival(WorldPoint at)
		{
			if (at == null || destination == null || destination.size() != 3) { return false; }
			WorldPoint arrival = new WorldPoint(destination.get(0), destination.get(1), destination.get(2));
			return arrival.getPlane() == at.getPlane() && arrival.distanceTo2D(at) <= 8;
		}

		public boolean matchesDeparture(int entityId, WorldPoint point, int range)
		{
			if (point == null) { return false; }
			for (Departure d : getDepartures())
			{
				WorldPoint origin = d.point();
				if (d.id == entityId && origin != null && origin.getPlane() == point.getPlane()
					&& origin.distanceTo2D(point) <= range) { return true; }
			}
			return false;
		}

		public static class Departure
		{
			private int id;
			private String action;
			private List<Integer> origin;
			public int getId() { return id; }
			public String getAction() { return action; }
			private WorldPoint point()
			{
				return origin == null || origin.size() != 3 ? null
					: new WorldPoint(origin.get(0), origin.get(1), origin.get(2));
			}
		}

		/** Where the guide said it was going. */
		public String getPlace()
		{
			return place == null ? "" : place;
		}

		/** "boats", "minecarts", "magic_carpets" -- which table this came from. */
		public String getKind()
		{
			return kind == null ? "" : kind;
		}

		/**
		 * The npc or object to click.
		 *
		 * <p>Which of the two it is, is deliberately not recorded: RuneLite's
		 * npc and object id spaces overlap -- 492 transport ids exist in both,
		 * and 7789 is Holgart and also a calquat tree -- so naming a kind here
		 * would be a guess. The scene is asked about both and answers.
		 */
		public int getId()
		{
			return id;
		}

		/** "Rimmington Captain Barnaby": where it goes, and who takes you. */
		public String getAction()
		{
			return action == null ? "" : action;
		}

		/**
		 * Every place this vehicle can be boarded for that destination.
		 *
		 * <p>Several docks sail to the same island, and which one is wanted is
		 * wherever the player happens to be standing.
		 */
		public List<List<Integer>> getOrigins()
		{
			List<List<Integer>> points = new ArrayList<>();
			for (Departure d : getDepartures()) { if (d.point() != null) { points.add(d.origin); } }
			return points;
		}

		public List<Integer> getDestination()
		{
			return destination == null ? Collections.emptyList() : destination;
		}
	}
	private Completion completion;
	private Approach approach;
	private String questHelper;
	private String diaryTask;
	private boolean questStep;
	private String questContext;
	private String questSideTaskOf;
	private Integer questSideTaskFrom;

	public String getQuestSideTaskOf() { return questSideTaskOf; }
	public Integer getQuestSideTaskFrom() { return questSideTaskFrom; }
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

	/**
	 * Quest coordinates only own navigation when the guide has not named its
	 * own entity. Dialogue still uses the original instruction. Keep this rule
	 * shared: the route, rim arrow and fallback tile previously disagreed when
	 * a named NPC was not loaded and the quest still described an earlier task.
	 */
	public QuestHelperSteps.Instruction navigationInstruction(QuestHelperSteps.Instruction instruction)
	{
		return target != null && !target.getIds().isEmpty() && !questFollow
			? null : instruction;
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

	/**
	 * Renumber this step so it follows the ones before it in its new section.
	 *
	 * <p>Only {@link Section#absorb} calls this. A continuation section starts
	 * its own count at zero, so folding one into the bank it continues would
	 * otherwise produce 0..14 followed by 0..8 -- and WithdrawTracker reads
	 * "ordinal below the current step" as "already done", which would quietly
	 * drop every item the second half of the bank asks for.
	 */
	void renumber(int position)
	{
		ordinal = position;
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
