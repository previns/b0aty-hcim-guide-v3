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
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Auto-ticking is the only thing this plugin does that can destroy work
 * silently: a step wrongly marked complete is skipped, and the player finds out
 * much later. The bit arithmetic is therefore tested directly rather than only
 * through a running client.
 */
public class DiaryCompletionTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static List<Step> stepsWithCompletion(Guide guide)
	{
		final List<Step> found = new ArrayList<>();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getCompletion() != null)
				{
					found.add(step);
				}
			}
		}
		return found;
	}

	@Test
	public void theShippedGuideCarriesDiaryBits() throws Exception
	{
		final List<Step> steps = stepsWithCompletion(loader.loadBundled());
		assertFalse("guide.json should carry completion signals", steps.isEmpty());
		int diaries = 0;
		for (Step step : steps)
		{
			final Completion completion = step.getCompletion();
			// The field carries both kinds now. Each must answer for itself and
			// not for the other.
			assertTrue("a completion must be one kind or the other",
				completion.isDiary() ^ completion.isSkill());
			if (!completion.isDiary())
			{
				continue;
			}
			diaries++;
			assertTrue("varplayer id must be real", completion.getVarplayer() > 0);
			assertNotNull(completion.getVarplayerName());
			assertTrue("bit in range", completion.getBit() >= 0 && completion.getBit() <= 31);
		}
		assertTrue("the guide should still carry diary bits", diaries > 100);
	}

	@Test
	public void hansIsBitFiveOfTheLumbridgeDiary() throws Exception
	{
		// The step the bug report came from. Pinned because it is the cheapest
		// possible check that the whole chain -- quest-helper extraction,
		// curated mapping, VarPlayer lookup, emit -- still lines up.
		Step hans = null;
		for (Step step : stepsWithCompletion(loader.loadBundled()))
		{
			if (step.getText().startsWith("Check playtime on Hans"))
			{
				hans = step;
			}
		}
		assertNotNull("the Hans step should have a completion signal", hans);
		assertEquals("LUMB_DRAY_ACHIEVEMENT_DIARY", hans.getCompletion().getVarplayerName());
		assertEquals(1194, hans.getCompletion().getVarplayer());
		assertEquals(5, hans.getCompletion().getBit());
	}

	@Test
	public void aSetBitReadsAsComplete() throws Exception
	{
		final Completion hans = completionFor("Check playtime on Hans");
		assertTrue(hans.isSetIn(1 << 5));
		assertTrue("other bits set alongside it must not matter", hans.isSetIn(0b11111111));
	}

	@Test
	public void anUnsetBitReadsAsIncomplete() throws Exception
	{
		final Completion hans = completionFor("Check playtime on Hans");
		assertFalse(hans.isSetIn(0));
		assertFalse("a neighbouring task must not tick this one", hans.isSetIn(1 << 6));
		assertFalse(hans.isSetIn(1 << 4));
	}

	@Test
	public void theTopBitDoesNotSignExtend()
	{
		// A VarPlayer is a signed int. Bit 31 set makes the value negative, and
		// an arithmetic shift would smear ones downward and tick every task in
		// the diary at once.
		final Completion completion = new Gson().fromJson(
			"{\"kind\":\"diary\",\"varplayer\":1194,\"bit\":3}", Completion.class);
		assertFalse("bit 3 must stay clear when only bit 31 is set",
			completion.isSetIn(1 << 31));
	}

	@Test
	public void aStepWithNoSignalIsNeverAutoTicked() throws Exception
	{
		final Guide guide = loader.loadBundled();
		int without = 0;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getCompletion() == null)
				{
					without++;
				}
			}
		}
		// The overwhelming majority. If this ever inverts, something started
		// inferring completion rather than proving it.
		assertTrue("most steps must have no completion signal",
			without > guide.getStepCount() / 2);
	}

	private Completion completionFor(String prefix) throws Exception
	{
		for (Step step : stepsWithCompletion(loader.loadBundled()))
		{
			if (step.getText().startsWith(prefix))
			{
				return step.getCompletion();
			}
		}
		throw new AssertionError("no step starting " + prefix);
	}
}
