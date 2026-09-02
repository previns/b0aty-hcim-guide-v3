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

import com.b0atyguide.data.Completion;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.b0atyguide.progress.AutoTick;
import com.b0atyguide.progress.Progress;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The diary auto-tick, driven by a fake VarPlayer reader.
 *
 * <p>Worth testing without a client because this is the only code that marks
 * work done on the player's behalf, and because the bug it was extracted from
 * -- calling Quest.getState from inside a client script -- hung the game at
 * login with no clue in the panel.
 */
public class AutoTickTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static Step diaryStep(Guide guide, String prefix)
	{
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getCompletion() != null && step.getText().startsWith(prefix))
				{
					return step;
				}
			}
		}
		throw new AssertionError("no diary step starting " + prefix);
	}

	/** Every VarPlayer reads as zero: nothing is done. */
	private static final IntUnaryOperator NOTHING_DONE = id -> 0;

	@Test
	public void nothingIsTickedWhenNoBitIsSet() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Progress progress = new Progress();
		assertEquals(0, AutoTick.diaries(guide, progress, NOTHING_DONE));
		assertEquals(0, progress.count());
	}

	@Test
	public void aSetBitTicksExactlyItsOwnStep() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Step hans = diaryStep(guide, "Check playtime on Hans");
		final Completion completion = hans.getCompletion();

		final Progress progress = new Progress();
		final int ticked = AutoTick.diaries(guide, progress,
			id -> id == completion.getVarplayer() ? (1 << completion.getBit()) : 0);

		assertEquals("only the Hans step should tick", 1, ticked);
		assertTrue(progress.isComplete(hans.getId()));
	}

	@Test
	public void aNeighbouringBitDoesNotTickThisStep() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Step hans = diaryStep(guide, "Check playtime on Hans");
		final Completion completion = hans.getCompletion();

		final Progress progress = new Progress();
		AutoTick.diaries(guide, progress,
			id -> id == completion.getVarplayer() ? (1 << (completion.getBit() + 1)) : 0);

		assertFalse(progress.isComplete(hans.getId()));
	}

	@Test
	public void everyBitSetTicksOnlyStepsThatHaveASignal() throws Exception
	{
		// The dangerous direction: a step with no completion signal must never
		// be ticked, whatever the game reports.
		final Guide guide = loader.loadBundled();
		final Progress progress = new Progress();
		AutoTick.diaries(guide, progress, id -> -1);

		final Set<String> withSignal = new HashSet<>();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getCompletion() != null)
				{
					withSignal.add(step.getId());
				}
			}
		}
		assertFalse(withSignal.isEmpty());
		for (String id : progress.completedIds())
		{
			assertTrue("ticked a step with no completion signal: " + id,
				withSignal.contains(id));
		}
	}

	@Test
	public void autoTickedStepsAreNotMarkedManual() throws Exception
	{
		// "Un-complete this bank" keeps manually ticked steps. An auto-tick is
		// not the player's work, so it must not survive that.
		final Guide guide = loader.loadBundled();
		final Step hans = diaryStep(guide, "Check playtime on Hans");
		final Progress progress = new Progress();
		AutoTick.diaries(guide, progress, id -> -1);
		assertTrue(progress.isComplete(hans.getId()));
	}

	@Test
	public void alreadyCompletedStepsAreNotCountedTwice() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Progress progress = new Progress();
		final int first = AutoTick.diaries(guide, progress, id -> -1);
		final int second = AutoTick.diaries(guide, progress, id -> -1);
		assertTrue(first > 0);
		assertEquals("a second pass has nothing left to do", 0, second);
	}

	@Test
	public void aNullGuideIsNotAnError()
	{
		assertEquals(0, AutoTick.diaries(null, new Progress(), NOTHING_DONE));
	}

	@Test
	public void theVarpReaderIsOnlyAskedForKnownVarplayers() throws Exception
	{
		// If this ever asks for something else, the caller can no longer pass
		// client::getVarpValue safely from inside a script.
		final Guide guide = loader.loadBundled();
		final Set<Integer> asked = new HashSet<>();
		AutoTick.diaries(guide, new Progress(), id ->
		{
			asked.add(id);
			return 0;
		});
		assertFalse(asked.isEmpty());
		for (Integer id : asked)
		{
			assertNotNull(id);
			assertTrue("varplayer id should be real: " + id, id > 0);
		}
	}

	// --- skill targets ------------------------------------------------------

	@Test
	public void aSkillStepTicksOnlyOnceTheLevelIsReached() throws Exception
	{
		final Guide guide = loader.loadBundled();
		Step target = null;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getCompletion() != null && step.getCompletion().isSkill())
				{
					target = step;
					break;
				}
			}
		}
		assertNotNull("the guide should carry skill targets", target);
		final int needed = target.getCompletion().getLevel();
		final String skill = target.getCompletion().getSkill();

		final Progress below = new Progress();
		AutoTick.skills(guide, below, name -> skill.equals(name) ? needed - 1 : 1);
		assertFalse("one level short is not done", below.isComplete(target.getId()));

		final Progress at = new Progress();
		AutoTick.skills(guide, at, name -> skill.equals(name) ? needed : 1);
		assertTrue("reaching the level finishes it", at.isComplete(target.getId()));
	}

	@Test
	public void aHigherLevelStillCounts() throws Exception
	{
		// Someone arriving already past the target should not be left stuck.
		final Guide guide = loader.loadBundled();
		final Progress progress = new Progress();
		final int ticked = AutoTick.skills(guide, progress, name -> 99);
		assertTrue("maxed accounts should tick every skill step", ticked > 0);
	}

	@Test
	public void noSkillProgressTicksNothing() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Progress progress = new Progress();
		assertEquals(0, AutoTick.skills(guide, progress, name -> 0));
	}

	@Test
	public void anUnknownSkillNameIsNotAnError() throws Exception
	{
		// The plugin maps an unknown name to 0 rather than throwing, which
		// happens only if the data was built against a newer RuneLite.
		final Guide guide = loader.loadBundled();
		final Progress progress = new Progress();
		assertEquals(0, AutoTick.skills(guide, progress, name -> 0));
	}

	@Test
	public void aSkillStepIsNeverTickedByADiaryBit() throws Exception
	{
		// The two share the completion field, so they must not answer for
		// each other.
		final Guide guide = loader.loadBundled();
		final Progress progress = new Progress();
		AutoTick.diaries(guide, progress, id -> -1);
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getCompletion() != null && step.getCompletion().isSkill())
				{
					assertFalse("a skill step ticked from a diary bit: " + step.getText(),
						progress.isComplete(step.getId()));
				}
			}
		}
	}
}
