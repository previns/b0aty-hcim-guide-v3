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
 * <p>Two things keep this honest.
 *
 * <p><b>A baseline, not an absolute.</b> Nothing here knows which value belongs
 * to which step -- that would need a curated mapping per step per quest. What it
 * knows is where the quest stood when the step became current, so only movement
 * from that point counts. A player who already finished the quest sees no
 * spurious tick.
 *
 * <p><b>Only steps the guide tagged.</b> A quest named in a step's prose is not
 * evidence: "Dragon Slayer" appears in sentences that are not about doing Dragon
 * Slayer, and the guide mentions quests in passing constantly. Only a step
 * carrying a real {@code [Quest Name]} tag is eligible, which is why
 * {@link #isEligible} checks for one.
 */
@Singleton
public class QuestProgress
{
	/** Which step the baseline belongs to, and what the quest read then. */
	private String watchedStepId;
	private int baseline;
	private boolean watching;

	/**
	 * Whether a step's completion can be read from quest progress at all.
	 *
	 * <p>Needs both halves: a quest helper to say where the progress lives, and
	 * a tag to say the guide really is sending the player at that quest.
	 */
	public static boolean isEligible(Guide guide, Step step)
	{
		return step != null
			&& step.getVerifiableQuest() != null
			&& guide != null
			&& guide.questHelperFor(step) != null;
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
		watching = true;
	}

	/**
	 * Whether the watched step's quest has moved on since {@link #watch}.
	 *
	 * <p>Strictly greater, never merely different: a quest value can drop when a
	 * quest is restarted or an item lost, and that is not progress.
	 */
	public boolean hasAdvanced(Guide guide, Step current, IntUnaryOperator varbit,
		IntUnaryOperator varp)
	{
		if (!watching || current == null || !current.getId().equals(watchedStepId)
			|| !isEligible(guide, current))
		{
			return false;
		}
		return read(guide, current, varbit, varp) > baseline;
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
