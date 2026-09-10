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
package com.b0atyguide.progress;

import com.b0atyguide.data.Guide;
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import javax.inject.Singleton;

/**
 * Ticks a step when the quest it names moves forward while that step is
 * current.
 *
 * <p>The route does "macro questing" -- a step or two of a quest in passing --
 * so most of these steps never finish a quest and {@code Quest.getState} never
 * reports them done. But the quest's own progress value is game state, and it
 * moving while the player is stood on "Start X Marks the Spot on Veos" is the
 * game saying that step happened.
 *
 * <p>Only steps the pipeline identifies as doing the quest are eligible; a
 * bracket naming the reason for an errand is not enough. Inferred later-visit
 * boundaries require movement from a baseline because an already-reached coarse
 * quest value cannot prove a finer-grained guide action happened. Explicit item
 * goals and start-only objectives instead carry direct evidence from the data,
 * so they can also be recognised when resuming. Unresolved item goals never
 * fall back to an unrelated quest-progress change.
 */
@Singleton
public class QuestProgress
{
	/** Which step the baseline belongs to, and what the quest read then. */
	private String watchedStepId;
	private int baseline;

	/**
	 * Where Quest Helper stood when the step opened, or {@link Integer#MIN_VALUE}
	 * when it could not be read -- which is below every real position, so an
	 * unknown start never blocks a hand-over.
	 */
	private int basePanel = Integer.MIN_VALUE;
	private boolean watching;

	/**
	 * Whether a step's completion can be read from quest progress at all.
	 *
	 * <p>Needs both halves: a quest helper to say where the progress lives, and
	 * the build's word that this step really is the quest being done.
	 *
	 * <p>A tag on its own is not that word. The guide names a quest as the
	 * <em>reason</em> for a step at least as often as its subject -- "Take 1
	 * extra Rotten Apple [Mournings End Pt 1]" is collecting an apple a hundred
	 * banks before that quest -- and reading the bracket as "you are doing
	 * this" offered to tick the step whenever that quest moved. The build
	 * decides, and it is stricter than a bracket.
	 */
	public static boolean isEligible(Guide guide, Step step)
	{
		return step != null
			&& step.isQuestStep()
			&& guide != null
			&& guide.questHelperFor(step) != null;
	}

	public static boolean canCompleteFromQuestState(Step step)
	{
		// The finished-quest sweep visits the WHOLE guide, unlike milestone
		// evaluation. It must not bypass explicit goals: finishing a quest does
		// not prove we now hold an extra stack, or settle an unresolved objective.
		return step != null && step.isQuestStep() && !step.isQuestStopUnresolved()
			&& step.getQuestStopItems().isEmpty() && step.getQuestStopValue() == null
			&& step.getQuestStopCondition() == null;
	}

	/** Forget the baseline. The step changed, or the plugin is stopping. */
	public void clear()
	{
		watchedStepId = null;
		watching = false;
		baseline = 0;
	}

	/**
	 * Note where the quest stands, so later movement can be recognised.
	 *
	 * <p>Safe to call every tick: it only takes a reading the first time it sees
	 * a given step.
	 */
	public void watch(Guide guide, Step current, IntUnaryOperator varbit, IntUnaryOperator varp)
	{
		watch(guide, current, varbit, varp, null);
	}

	/**
	 * The same, told where Quest Helper stands as the step opens.
	 *
	 * <p>Remembered so a hand-over the quest had already reached can be told
	 * from one it reaches while the step is open. Without it the step ticks the
	 * moment it appears, which is the failure this mechanism exists to prevent.
	 */
	public void watch(Guide guide, Step current, IntUnaryOperator varbit,
		IntUnaryOperator varp, Integer panelNow)
	{
		if (!isEligible(guide, current))
		{
			clear();
			return;
		}
		if (watching && current.getId().equals(watchedStepId))
		{
			return;
		}
		watchedStepId = current.getId();
		baseline = read(guide, current, varbit, varp);
		basePanel = panelNow == null ? Integer.MIN_VALUE : panelNow;
		watching = true;
	}

	/**
	 * Whether the quest has got as far as this step was meant to take it.
	 *
	 * <p>Three rules, in the order the build could establish them:
	 *
	 * <ol>
	 * <li>{@code questDoneAt} -- the quest has reached where the <em>next</em>
	 *     guide step begins. This is the honest answer, and the one that stops
	 *     "continue Gertrude's Cat" ticking after the ladder when the milk, the
	 *     sardine and the kitten are still to come.
	 * <li>{@code questCompletes} -- the guide's last step for that quest, done
	 *     once the quest is past everything Quest Helper describes.
	 * <li>otherwise, the quest moved at all since the step became current.
	 *     Right for a step that is one instruction long, which most are, and no
	 *     worse than before for the rest.
	 * </ol>
	 *
	 * <p>The first two are absolute, and can be: the build worked out which
	 * value belongs to which step, which is exactly what it could not do when
	 * the baseline rule was written. The third is still strictly greater than
	 * the baseline -- a quest value drops when a quest is restarted or an item
	 * lost, and that is not progress.
	 */
	public boolean hasAdvanced(Guide guide, Step current, IntUnaryOperator varbit,
		IntUnaryOperator varp)
	{
		return hasAdvanced(guide, current, varbit, varp, null);
	}

	/**
	 * The same, told where Quest Helper currently is in its own list of steps.
	 *
	 * <p>{@code panelNow} is the position of the instruction actually being
	 * shown -- after the branch conditions have been read -- which is the only
	 * fine-grained "how far along" this plugin has. A quest's progress value
	 * moves a handful of times; Quest Helper's sidebar has an entry for every
	 * action, and the guide's own steps are written at that granularity.
	 */
	public boolean hasAdvanced(Guide guide, Step current, IntUnaryOperator varbit,
		IntUnaryOperator varp, Integer panelNow)
	{
		return hasAdvanced(guide, current, varbit, varp, panelNow, Collections.emptyMap());
	}

	/** Item milestones can occur without any quest var or sidebar movement. */
	public boolean hasAdvanced(Guide guide, Step current, IntUnaryOperator varbit,
		IntUnaryOperator varp, Integer panelNow, Map<Integer, Integer> carried)
	{
		if (!watching || current == null || !current.getId().equals(watchedStepId)
			|| !isEligible(guide, current))
		{
			return false;
		}
		if (current.isQuestStopUnresolved())
		{
			return false;
		}
		if (!current.getQuestStopItems().isEmpty() || current.getQuestStopValue() != null
			|| current.getQuestStopCondition() != null)
		{
			// Owning the specified items is direct evidence. A future quest visit,
			// or reading the book after receiving it, is not this guide step's goal.
			return explicitGoalSatisfied(guide, current, varbit, varp, carried);
		}

		final int now = read(guide, current, varbit, varp);
		if (current.isQuestStartOnly() && current.getQuestDoneAt() != null)
		{
			// Unlike an inferred later-visit boundary, starting is a precise
			// objective. Also recognise it after a restart with the quest begun;
			// the player cannot repeat the initial conversation to move our baseline.
			return now >= current.getQuestDoneAt();
		}

		// Where the guide hands the quest back to itself: run it to a stated
		// point, go and do something else, come back and finish it. That point
		// is Quest Helper's last step, and reaching it is what ends this one.
		// The progress value cannot say -- burning four sets of sheep bones
		// does not move it at all -- so this is checked first.
		final Integer handOver = current.getQuestDoneAtPanel();
		if (handOver != null)
		{
			// A missing/unproven panel is not permission to substitute the
			// coarse var boundary. That var cannot prove the finer action.
			if (panelNow == null) { return false; }
			// Better information than the progress value, so it settles the
			// question by itself: not yet there means not yet done, and the
			// value rules below are not consulted. An unknown position is not
			// evidence that this finer-grained objective has happened either.
			//
			// Guarded the same way the progress value is, and for the same
			// reason: a hand-over already reached when the step opened cannot
			// tell this step from the one before it, so acting on it ticks the
			// step the instant it appears. That is the bug this whole mechanism
			// was built to fix, and it would have come straight back in.
			return basePanel < handOver && panelNow >= handOver;
		}

		final Integer doneAt = current.getQuestDoneAt();
		if (doneAt != null)
		{
			// A boundary the quest had already passed when the step opened says
			// nothing about this step, and treating it as met ticks the step the
			// instant it appears. "Continue Sheep Herder until all 4 Sheep bones
			// are burnt" is the shape: its boundary is 2, and the quest is
			// already at 2 the moment the step becomes current, because burning
			// bones is something the quest's own progress value never mentions.
			// The guide walked straight past four sheep.
			//
			// Where the guide is finer-grained than the quest, the honest answer
			// is that the plugin cannot tell, so the step waits to be ticked by
			// hand.
			return baseline < doneAt && now >= doneAt;
		}
		if (current.isQuestCompletes())
		{
			final QuestHelperSteps helper = guide.questHelperFor(current);
			return now > 0 && helper != null && helper.at(now) == null;
		}
		return now > baseline;
	}

	/**
	 * Direct evidence of the stated objective. Inferred boundaries keep their
	 * baseline guard: passing a later visit is not proof of this visit's task.
	 * Verified goals also work on resume, when the original action cannot be
	 * repeated. Guidance and ticking must use the same predicate or QH will
	 * continue past the stopping point while the checkbox waits for a game tick.
	 */
	public static boolean explicitGoalSatisfied(Guide guide, Step step,
		IntUnaryOperator varbit, IntUnaryOperator varp, Map<Integer, Integer> carried)
	{
		if (step == null || step.isQuestStopUnresolved())
		{
			return false;
		}
		if (step.getQuestStopCondition() != null)
		{
			return step.getQuestStopCondition().milestoneSatisfied(new QuestHelperSteps.Vars()
			{
				public int varbit(int id) { return varbit.applyAsInt(id); }
				public int varplayer(int id) { return varp.applyAsInt(id); }
			});
		}
		return step.getQuestStopValue() != null
			? read(guide, step, varbit, varp) >= step.getQuestStopValue()
			: stopItemsSatisfied(step, carried);
	}

	/** Current inventory/equipment only; banked items do not mean 'received now'. */
	public static Map<Integer, Integer> guidanceInventory(Step step, Map<Integer, Integer> carried)
	{
		// QH may only require one ingredient; the guide may explicitly collect
		// three before leaving. For branch selection only, an incomplete stack
		// must not trigger QH's "leave / grind / use it" branch. Never mutate the
		// actual inventory snapshot: completion, bank counts and overlays use it.
		Map<Integer, Integer> adjusted = null;
		for (QuestHelperSteps.Need need : step.getQuestStopItems())
		{
			long count = 0;
			for (int id : need.getIds()) { count += Math.max(0, carried.getOrDefault(id, 0)); }
			if (count > 0 && count < need.getCount())
			{
				if (adjusted == null) { adjusted = new HashMap<>(carried); }
				for (int id : need.getIds()) { adjusted.remove(id); }
			}
		}
		return adjusted == null ? carried : adjusted;
	}

	/** Current inventory/equipment only; banked items do not mean 'received now'. */
	public static boolean stopItemsSatisfied(Step step, Map<Integer, Integer> carried)
	{
		if (step == null || step.getQuestStopItems().isEmpty() || carried == null)
		{
			return false;
		}
		for (QuestHelperSteps.Need need : step.getQuestStopItems())
		{
			long count = 0;
			for (Integer id : need.getIds())
			{
				count += Math.max(0, carried.getOrDefault(id, 0));
			}
			if (count < need.getCount())
			{
				return false;
			}
		}
		return true;
	}

	private static int read(Guide guide, Step step, IntUnaryOperator varbit,
		IntUnaryOperator varp)
	{
		final QuestHelperSteps helper = guide.questHelperFor(step);
		final QuestHelperSteps.Var var = helper == null ? null : helper.getVar();
		if (var == null)
		{
			return 0;
		}
		return var.isVarbit() ? varbit.applyAsInt(var.getId()) : varp.applyAsInt(var.getId());
	}
}
