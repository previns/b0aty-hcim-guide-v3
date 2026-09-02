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
package com.b0atyguide;

import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.b0atyguide.progress.QuestProgress;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Ticking a step because the quest it names moved forward.
 *
 * <p>This is the loosest of the three auto-tick signals -- the other two read a
 * bit or a quest state that means exactly one thing -- so the guards matter more
 * than the happy path, and most of these test a guard.
 */
public class QuestProgressTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static final IntUnaryOperator ZERO = id -> 0;

	private static List<Step> steps(Guide guide)
	{
		final List<Step> all = new ArrayList<>();
		for (Section section : guide.getSections())
		{
			all.addAll(section.getSteps());
		}
		return all;
	}

	/** A step with both a quest tag and a helper -- the eligible kind. */
	private static Step eligible(Guide guide)
	{
		for (Step step : steps(guide))
		{
			if (QuestProgress.isEligible(guide, step))
			{
				return step;
			}
		}
		throw new AssertionError("no eligible step in the guide");
	}

	@Test
	public void theGuideHasStepsThisCanTick() throws Exception
	{
		final Guide guide = loader.loadBundled();
		int eligible = 0;
		for (Step step : steps(guide))
		{
			if (QuestProgress.isEligible(guide, step))
			{
				eligible++;
			}
		}
		assertTrue("expected a useful number of eligible steps", eligible > 100);
	}

	@Test
	public void aQuestNamedOnlyInProseIsNotEligible() throws Exception
	{
		// "Dragon Slayer" turns up in sentences that are not about doing Dragon
		// Slayer. Those steps get the overlay text and nothing else.
		final Guide guide = loader.loadBundled();
		int proseOnly = 0;
		for (Step step : steps(guide))
		{
			if (step.getQuestHelper() != null && step.getVerifiableQuest() == null)
			{
				assertFalse(step.getText(), QuestProgress.isEligible(guide, step));
				proseOnly++;
			}
		}
		assertTrue("the guide names quests in prose too", proseOnly > 0);
	}

	@Test
	public void movementAfterReachingTheStepTicksIt() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Step step = eligible(guide);
		final QuestProgress watcher = new QuestProgress();

		watcher.watch(guide, step, id -> 3, id -> 3);
		assertFalse("nothing has moved yet",
			watcher.hasAdvanced(guide, step, id -> 3, id -> 3));
		assertTrue("the quest moved on",
			watcher.hasAdvanced(guide, step, id -> 4, id -> 4));
	}

	@Test
	public void aQuestAlreadyPastThisPointTicksNothing() throws Exception
	{
		// The player who did the quest last week. The baseline is taken where
		// they are, so there is no movement to see.
		final Guide guide = loader.loadBundled();
		final Step step = eligible(guide);
		final QuestProgress watcher = new QuestProgress();

		watcher.watch(guide, step, id -> 99, id -> 99);
		assertFalse(watcher.hasAdvanced(guide, step, id -> 99, id -> 99));
	}

	@Test
	public void goingBackwardsIsNotProgress()
	{
		// A quest value can drop -- restarting, losing an item. That is not a
		// step being completed.
		final Guide guide;
		try
		{
			guide = loader.loadBundled();
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
		final Step step = eligible(guide);
		final QuestProgress watcher = new QuestProgress();

		watcher.watch(guide, step, id -> 5, id -> 5);
		assertFalse(watcher.hasAdvanced(guide, step, id -> 2, id -> 2));
	}

	@Test
	public void theBaselineFollowsTheCurrentStep() throws Exception
	{
		// Moving to another step must not leave the previous step's reading
		// behind, or the next quest movement ticks the wrong thing.
		final Guide guide = loader.loadBundled();
		final List<Step> eligible = new ArrayList<>();
		for (Step step : steps(guide))
		{
			if (QuestProgress.isEligible(guide, step))
			{
				eligible.add(step);
			}
			if (eligible.size() == 2)
			{
				break;
			}
		}
		assertTrue(eligible.size() == 2);

		final QuestProgress watcher = new QuestProgress();
		watcher.watch(guide, eligible.get(0), id -> 1, id -> 1);
		watcher.watch(guide, eligible.get(1), id -> 7, id -> 7);

		assertFalse("the first step is no longer watched",
			watcher.hasAdvanced(guide, eligible.get(0), id -> 9, id -> 9));
		assertTrue("the second step is",
			watcher.hasAdvanced(guide, eligible.get(1), id -> 8, id -> 8));
	}

	@Test
	public void anIneligibleStepIsNeverTicked() throws Exception
	{
		final Guide guide = loader.loadBundled();
		Step plain = null;
		for (Step step : steps(guide))
		{
			if (!QuestProgress.isEligible(guide, step))
			{
				plain = step;
				break;
			}
		}
		assertNotNull(plain);

		final QuestProgress watcher = new QuestProgress();
		watcher.watch(guide, plain, ZERO, ZERO);
		assertFalse(watcher.hasAdvanced(guide, plain, id -> 100, id -> 100));
	}

	@Test
	public void clearingStopsTheWatch() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Step step = eligible(guide);
		final QuestProgress watcher = new QuestProgress();

		watcher.watch(guide, step, id -> 1, id -> 1);
		watcher.clear();
		assertFalse(watcher.hasAdvanced(guide, step, id -> 5, id -> 5));
	}

	@Test
	public void aNullStepIsSafe() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final QuestProgress watcher = new QuestProgress();
		watcher.watch(guide, null, ZERO, ZERO);
		assertFalse(watcher.hasAdvanced(guide, null, ZERO, ZERO));
	}
}
