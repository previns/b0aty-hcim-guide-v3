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

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;

/**
 * Quest Helper's instructions for one quest, keyed by that quest's own progress
 * value.
 *
 * <p>The guide advances a quest a step or two in passing rather than doing it in
 * one sitting, so a player following it needs to know what the next step of the
 * quest is at whatever value they are at. Reading the quest's varbit gives that
 * directly.
 */
public class QuestHelperSteps
{
	public static class Var
	{
		private String kind;
		private int id;
		private String constant;

		/** "varbit" or "varplayer" -- the two places quest progress is stored. */
		public String getKind()
		{
			return kind;
		}

		public int getId()
		{
			return id;
		}

		public String getConstant()
		{
			return constant;
		}

		public boolean isVarbit()
		{
			return "varbit".equals(kind);
		}
	}

	public static class Instruction
	{
		/** How deep a conditional may nest before this stops following it. */
		private static final int MAX_NESTING = 8;

		private String kind;
		private String constant;
		private List<Integer> point;
		private List<List<Integer>> tiles;
		private Integer panel;
		private List<WidgetMark> widgets;
		private List<Integer> ids;
		private String text;
		private boolean conditional;
		private int branches;
		private List<Need> items;
		private int icon;
		private boolean spread;
		private List<String> dialogue;
		private Requirement when;
		private List<Instruction> whenIn;

		/**
		 * Whether every match counts, not only the one where the step points.
		 *
		 * <p>Quest Helper's own {@code allowMultipleHighlights} /
		 * {@code showAllInArea}. The kitten could be in any of the Lumberyard
		 * crates, and marking the nearest one is no help at all -- while the
		 * Lumberyard ladder is one of many with the same id across Varrock and
		 * marking all of them is no help either. The flag is which of the two
		 * this is, and it is quest-helper's answer, not a guess.
		 */
		public boolean isSpread()
		{
			return spread;
		}

		/**
		 * The dialogue options Quest Helper says to pick, by their words.
		 *
		 * <p>The words, not the position. Quest Helper highlights whichever
		 * visible option says one of these, at any point in the conversation,
		 * which is why it never loses its place -- while counting has to be
		 * right about every answer before this one to be right about this one.
		 *
		 * <p>A set rather than a sequence: an option is the right option
		 * whenever it is on screen.
		 */
		public List<String> getDialogue()
		{
			return dialogue == null ? Collections.emptyList() : dialogue;
		}

		/**
		 * An item to draw over the thing this step names, or 0.
		 *
		 * <p>Quest Helper's own way of saying "use this on that": the sprite
		 * goes on the npc and the item lights up in the inventory. It is the
		 * clearest instruction the plugin gives, and it says it 918 times.
		 */
		public int getIcon()
		{
			return icon;
		}

		/**
		 * The branches this instruction has that turn on where the player is.
		 *
		 * <p>A Quest Helper ConditionalStep returns the first branch whose
		 * conditions hold, and a third of all its branch conditions are a bare
		 * zone -- "are you upstairs in the Lumberyard yet". A zone is a box, so
		 * that question can be answered here exactly, without any of the
		 * condition engine this plugin does not reproduce.
		 */
		public List<Instruction> getWhenIn()
		{
			return whenIn == null ? Collections.emptyList() : whenIn;
		}

		/**
		 * This instruction, or the branch whose condition holds right now.
		 *
		 * <p>In declaration order, first match wins -- Quest Helper's own rule,
		 * applied to the branches whose conditions can be answered exactly.
		 * Without it the guide kept saying "search the crates" after the player
		 * had the kitten in hand.
		 *
		 * @param at where the player is standing, or null
		 * @param held item id to how many are carried, inventory and worn
		 */
		public Instruction now(WorldPoint at, Map<Integer, Integer> held, Vars vars)
		{
			return now(at, held, vars, 0);
		}

		/**
		 * A chosen branch may be a conditional in its own right.
		 *
		 * <p>Quest Helper nests them freely -- a ConditionalStep handed to a
		 * ConditionalStep -- and 450 of the shipped branch sets sit inside
		 * another. Stopping at the first level answered those with the outer
		 * default, which for 79 of them carries no guidance at all.
		 *
		 * <p>Depth-limited because the data is generated: a cycle in it would
		 * otherwise be a hang rather than a wrong answer.
		 */
		private Instruction now(WorldPoint at, Map<Integer, Integer> held, Vars vars,
			int depth)
		{
			if (depth > MAX_NESTING)
			{
				return this;
			}
			for (Instruction branch : getWhenIn())
			{
				if (branch.when != null && branch.when.holds(at, held, vars))
				{
					return branch.now(at, held, vars, depth + 1);
				}
			}
			return this;
		}

		/**
		 * What this step of the quest needs in hand.
		 *
		 * <p>Quest Helper hands its item requirements to the individual step
		 * that needs them, and rings those in the player's inventory. The build
		 * used to take only the per-quest list, so a player halfway through was
		 * shown the whole shopping list or nothing at all.
		 *
		 * <p>Empty is the normal case: two thirds of the slots need nothing.
		 */
		public List<Need> getItems()
		{
			return items == null ? Collections.emptyList() : items;
		}

		/** "npc", "object" or "walk". */
		public String getKind()
		{
			return kind;
		}

		public String getConstant()
		{
			return constant;
		}

		public List<Integer> getPoint()
		{
			return point == null ? Collections.emptyList() : point;
		}

		/**
		 * Parts of an open interface to mark for this step.
		 *
		 * <p>While a menu is up there is nothing in the world to outline, so
		 * this is the whole of the guidance: which tool to click on the cannon,
		 * which line of the multi-skill menu, which slot of the shop.
		 */
		public List<WidgetMark> getWidgets()
		{
			return widgets == null ? Collections.emptyList() : widgets;
		}

		/**
		 * Where this comes in the quest, in Quest Helper's own order.
		 *
		 * <p>Its {@code getPanels()} -- the sidebar the player reads down -- is
		 * the only statement in the whole file of what order a quest's steps
		 * happen in. The progress value is too coarse (Sheep Herder has three
		 * values for a dozen actions) and a conditional's branches are ordered
		 * most-specific-first, which is not the same as first-to-last.
		 *
		 * <p>Null where the quest lists no panels, which is two of them.
		 */
		public Integer getPanel()
		{
			return panel;
		}

		/**
		 * Tiles Quest Helper marks on the floor for this step.
		 *
		 * <p>Its {@code addTileMarker}: the safespot to stand on, the square to
		 * drop the pet rock, the three tiles that are safe in Tarn's lair. For a
		 * step whose whole instruction is "stand here" they are the only thing
		 * on screen -- there is no npc and no scenery to outline -- so without
		 * them the guide draws a line to a room and then goes quiet.
		 */
		public List<List<Integer>> getTiles()
		{
			return tiles == null ? Collections.emptyList() : tiles;
		}

		/**
		 * Game ids for the thing this instruction names, so it can be outlined.
		 *
		 * <p>Resolved at build time from RuneLite's own id classes, because the
		 * plugin cannot look a constant name up at runtime. Empty where the
		 * constant is one Quest Helper declares itself, which still leaves the
		 * coordinate to route to.
		 */
		public List<Integer> getIds()
		{
			return ids == null ? Collections.emptyList() : ids;
		}

		/** Whether this names something in the scene rather than a place to walk. */
		public boolean isNpc()
		{
			return "npc".equals(kind);
		}

		public boolean isObject()
		{
			return "object".equals(kind);
		}

		public String getText()
		{
			return text == null ? "" : text;
		}

		/**
		 * True when this is a Quest Helper ConditionalStep's <em>default</em>
		 * branch rather than the branch its conditions would pick.
		 *
		 * <p>Reproducing those conditions means porting Quest Helper's
		 * requirement engine, which this plugin deliberately does not do. The
		 * default is right when you arrive at a step and can be stale once you
		 * are partway through it, so it must be shown as a hint and never as
		 * certainty.
		 */
		public boolean isConditional()
		{
			return conditional;
		}

		/** How many branches the real conditional had. More means less certain. */
		public int getBranches()
		{
			return branches;
		}
	}

	private Var var;
	private int lastValue;
	private List<Wanted> items;
	private Map<String, Instruction> steps;

	public Var getVar()
	{
		return var;
	}

	public Map<String, Instruction> getSteps()
	{
		return steps == null ? Collections.emptyMap() : steps;
	}

	/** Lazily built from {@link #getSteps()}; not deserialised. */
	private transient NavigableMap<Integer, Instruction> byValue;

	/**
	 * What Quest Helper says to do at this progress value.
	 *
	 * <p>Its steps are keyed at the values where the instruction <em>changes</em>
	 * -- 0, 5, 10, 20 -- while the quest's var takes every value in between. So
	 * the answer is the highest key at or below the current value, which is how
	 * Quest Helper reads its own table.
	 *
	 * <p>This used to be an exact lookup on the value. Measured against the
	 * shipped data that hits on 19% of the values in range, so four times out of
	 * five a player mid-quest was told nothing at all.
	 *
	 * @return the instruction in force, or null before the first keyed value
	 */
	public Instruction at(int value)
	{
		// Past the last value Quest Helper describes, the quest is over and
		// there is nothing to say. Without this the floor lookup answers with
		// the final instruction for ever -- "talk to Unferth to complete the
		// quest", on a step the player finished days ago.
		if (lastValue > 0 && value > lastValue)
		{
			return null;
		}

		final NavigableMap<Integer, Instruction> byValue = byValue();
		final Map.Entry<Integer, Instruction> entry = byValue.floorEntry(value);
		return entry == null ? null : entry.getValue();
	}

	/** The highest progress value this quest's steps describe. */
	public int getLastValue()
	{
		return lastValue;
	}

	/**
	 * The steps keyed by number rather than by the string Gson parsed them as,
	 * built once. Keys that are not numbers are dropped rather than throwing:
	 * a malformed one should cost that instruction, not the whole quest.
	 */
	private NavigableMap<Integer, Instruction> byValue()
	{
		NavigableMap<Integer, Instruction> local = byValue;
		if (local == null)
		{
			local = new TreeMap<>();
			for (Map.Entry<String, Instruction> entry : getSteps().entrySet())
			{
				try
				{
					local.put(Integer.parseInt(entry.getKey().trim()), entry.getValue());
				}
				catch (NumberFormatException ignored)
				{
					// Not a progress value, so nothing can be at it.
				}
			}
			byValue = local;
		}
		return local;
	}

	/**
	 * A branch's condition, in the shapes that can be answered exactly.
	 *
	 * <p>A zone is a box and the player is inside it or not; an item
	 * requirement is "do you hold N of these", which the inventory already
	 * answers for the bank ring; a var is an array read the client already does
	 * for the quest's own progress; and `all`, `any` and `not` join them. Quest Helper's
	 * remaining requirements -- its widget checks, its chat listeners, its own
	 * state machines -- are not reproduced, and a branch carrying one is not
	 * shipped at all rather than guessed at.
	 *
	 * <p>The vars are the ones that matter most by a distance: two thousand
	 * three hundred branch conditions are a varbit comparison, more than zones
	 * and items together. Without them this kept fewer than half of Quest
	 * Helper's branches, and the half it dropped included both of the branches
	 * in Monk's Friend that notice the player already has the blanket.
	 */
	public static class Requirement
	{
		private List<List<Integer>> zone;
		private Need item;
		private VarCheck var;
		private Present here;
		private Integer open;
		private List<Requirement> all;
		private List<Requirement> any;
		private Requirement not;

		/**
		 * Completion accepts only explicit state predicates, not transient scene,
		 * interface or negation fallbacks. An empty AND or an unsupported NOT
		 * could otherwise turn missing extraction into a completed guide step.
		 */
		public boolean isMilestoneCondition()
		{
			if (zone != null || item != null || here != null || open != null || not != null
				|| (var != null ? 1 : 0) + (all != null ? 1 : 0) + (any != null ? 1 : 0) != 1)
			{
				return false;
			}
			if (var != null)
			{
				return var.isValid();
			}
			List<Requirement> parts = all == null ? any : all;
			return !parts.isEmpty() && parts.stream().allMatch(p -> p != null && p.isMilestoneCondition());
		}

		public boolean milestoneSatisfied(Vars vars)
		{
			return holds(null, Collections.emptyMap(), vars);
		}

		boolean holds(WorldPoint at, Map<Integer, Integer> held, Vars vars)
		{
			// Quest Helper's LogicHelper: not(x) and nor(a, b) are its
			// NOR, which is "none of these". Six hundred and ninety-four branch
			// conditions are written with one of those helpers, and a branch
			// whose condition cannot be read is dropped rather than shipped.
			if (not != null)
			{
				return !not.holds(at, held, vars);
			}
			if (all != null)
			{
				for (Requirement part : all)
				{
					if (!part.holds(at, held, vars))
					{
						return false;
					}
				}
				return true;
			}
			if (any != null)
			{
				for (Requirement part : any)
				{
					if (part.holds(at, held, vars))
					{
						return true;
					}
				}
				return false;
			}
			if (var != null)
			{
				return vars != null && var.holds(vars);
			}
			if (here != null)
			{
				return vars != null && vars.here(here.kind, here.getIds(), here.getZone());
			}
			if (open != null)
			{
				return vars != null && vars.interfaceOpen(open);
			}
			if (item != null)
			{
				int carried = 0;
				for (Integer id : item.getIds())
				{
					final Integer some = held == null ? null : held.get(id);
					carried += some == null ? 0 : some;
				}
				return carried >= item.getCount();
			}
			if (zone != null && at != null)
			{
				for (List<Integer> box : zone)
				{
					if (box != null && box.size() >= 6
						&& box.get(0) <= at.getX() && at.getX() <= box.get(3)
						&& box.get(1) <= at.getY() && at.getY() <= box.get(4)
						&& box.get(2) <= at.getPlane() && at.getPlane() <= box.get(5))
					{
						return true;
					}
				}
			}
			return false;
		}
	}

	/**
	 * One part of an interface Quest Helper marks.
	 *
	 * <p>Its {@code WidgetHighlight}, kept whole: an interface to look in,
	 * optionally one child inside it, whether to search the children, and up to
	 * four filters. The filters are applied in Quest Helper's own order, and all
	 * of them must pass.
	 */
	public static class WidgetMark
	{
		/**
		 * The packed component id, written by the pipeline as "interface".
		 *
		 * <p>The name has to be mapped rather than matched: {@code interface} is
		 * a reserved word and cannot name a field. Without the mapping Gson
		 * found no key for {@code id}, left it at zero, and said nothing --
		 * and packed id zero is a real component, so every mark in the file
		 * pointed at the wrong thing instead of failing.
		 */
		@SerializedName("interface")
		private int id;
		private Integer child;
		private boolean children;
		private Integer item;
		private Integer model;
		private String says;
		private String named;

		/** The packed interface id, as {@code Client.getWidget(int)} takes it. */
		public int getId()
		{
			return id;
		}

		/** One child to descend into first, or null. */
		public Integer getChild()
		{
			return child;
		}

		/** Whether to search this widget's children rather than only itself. */
		public boolean searchesChildren()
		{
			return children;
		}

		/**
		 * Whether a widget passes every filter this mark carries.
		 *
		 * <p>Quest Helper's four checks, in its order and with its semantics:
		 * an item id and a model id are exact, the required text and the rough
		 * name are "contains". A filter that is not set passes.
		 */
		public boolean accepts(int itemId, int modelId, String text, String name)
		{
			if (item != null && itemId != item)
			{
				return false;
			}
			if (model != null && modelId != model)
			{
				return false;
			}
			if (says != null && (text == null || !text.contains(says)))
			{
				return false;
			}
			return named == null
				|| (name != null && !name.isEmpty() && name.contains(named));
		}
	}

	/**
	 * What the plugin can ask the running game on a condition's behalf.
	 *
	 * <p>Every method is a lookup, which is the line this draws: a var is an
	 * array read, and "is that npc here" is a walk of the scene the plugin
	 * already does every tick. None of it is Quest Helper's condition engine.
	 */
	public interface Vars
	{
		int varbit(int id);

		int varplayer(int id);

		/**
		 * Whether one of these is in the loaded scene, inside the box if given.
		 *
		 * @param kind "npc", "object" or "groundItem"
		 * @param ids  any one of which will do
		 * @param zone boxes to look in, or empty for the whole scene
		 */
		default boolean here(String kind, List<Integer> ids, List<List<Integer>> zone)
		{
			return false;
		}

		/**
		 * Whether that interface is on screen.
		 *
		 * <p>What makes a puzzle inside a menu answerable at all. Quest Helper's
		 * dwarf cannon repair is six branches turning on this and five varbits,
		 * and each of them names the part of the interface to click.
		 */
		default boolean interfaceOpen(int id)
		{
			return false;
		}
	}

	/**
	 * Something Quest Helper expects to find in the loaded scene.
	 *
	 * <p>"Is the npc standing there yet", "has the object appeared", "is the
	 * item on the floor" -- the questions that tell a quest's branches the
	 * player has done the thing. A hundred and eighty of this guide's branch
	 * conditions are one of these.
	 */
	public static class Present
	{
		private String kind;
		private List<Integer> ids;
		private List<List<Integer>> zone;

		public String getKind()
		{
			return kind;
		}

		public List<Integer> getIds()
		{
			return ids == null ? Collections.emptyList() : ids;
		}

		/** Boxes to look in, or empty for anywhere in the scene. */
		public List<List<Integer>> getZone()
		{
			return zone == null ? Collections.emptyList() : zone;
		}
	}

	/**
	 * A comparison against one of the game's own numbers.
	 *
	 * <p>Two forms, and they are different questions: "does this var equal 3"
	 * and "is bit 3 of this var set". Reading one as the other is silently
	 * wrong, so the build keeps them apart and so does this.
	 */
	public static class VarCheck
	{
		private String kind;
		private Integer id;
		private Integer value;
		private String op;
		private Integer bit;
		private Boolean set;

		private boolean isValid()
		{
			return ("varbit".equals(kind) || "varplayer".equals(kind)) && id != null && id >= 0
				&& (bit != null
					? bit >= 0 && bit <= 31 && value == null && op == null
					: value != null && set == null && (op == null
						|| java.util.Arrays.asList("==", "!=", ">", ">=", "<", "<=").contains(op)));
		}

		boolean holds(Vars vars)
		{
			if (id == null) { return false; }
			final int now = "varbit".equals(kind) ? vars.varbit(id) : vars.varplayer(id);
			if (bit != null)
			{
				final boolean isSet = ((now >> bit) & 1) == 1;
				return isSet == (set == null || set);
			}
			if (value == null)
			{
				return false;
			}
			switch (op == null ? "==" : op)
			{
				case ">":
					return now > value;
				case "<":
					return now < value;
				case "<=":
					return now <= value;
				case ">=":
					return now >= value;
				case "!=":
					return now != value;
				default:
					return now == value;
			}
		}
	}

	/**
	 * One item a single step of a quest needs, with every id that would do.
	 *
	 * <p>A requirement made from one of Quest Helper's item collections carries
	 * the whole set, best tier first -- "Pickaxe" is any pickaxe -- exactly as
	 * the guide's own category words do.
	 */
	public static class Need
	{
		private String name;
		private int count;
		private List<Integer> ids;

		public String getName()
		{
			return name == null ? "" : name;
		}

		/** How many, at least one. */
		public int getCount()
		{
			return Math.max(1, count);
		}

		public List<Integer> getIds()
		{
			return ids == null ? Collections.emptyList() : ids;
		}
	}

	/** One item a quest's helper says to bring. */
	public static class Wanted
	{
		private String name;
		private int id;

		public String getName()
		{
			return name;
		}

		public int getId()
		{
			return id;
		}
	}

	/**
	 * What this quest asks the player to bring.
	 *
	 * <p>Taken from Quest Helper's own ItemRequirement declarations, so the
	 * name and the number come from the same line of its source.
	 *
	 * <p>Per quest rather than per step. Which requirement belongs to which
	 * step is expressed in conditions this plugin does not evaluate, and
	 * pretending otherwise would put the wrong items in front of a player
	 * halfway through.
	 */
	public List<Wanted> getItems()
	{
		return items == null ? Collections.emptyList() : items;
	}
}
