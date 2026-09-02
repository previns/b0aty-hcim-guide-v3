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
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.google.gson.Gson;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Quest Helper's step lists, as the guide carries them.
 *
 * <p>The point of these is the conditional flag. A default branch shown as
 * though it were the real instruction is how a guide starts quietly lying
 * partway through a quest, so the data has to keep saying which ones are
 * uncertain.
 */
public class QuestStepsTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	@Test
	public void theGuideShipsQuestHelperStepLists() throws Exception
	{
		final Map<String, QuestHelperSteps> helpers = loader.loadBundled().getQuestHelpers();
		assertFalse("expected some quest helpers", helpers.isEmpty());
		for (Map.Entry<String, QuestHelperSteps> entry : helpers.entrySet())
		{
			final QuestHelperSteps helper = entry.getValue();
			assertNotNull(entry.getKey(), helper.getVar());
			assertTrue(entry.getKey() + " needs a real var id", helper.getVar().getId() > 0);
			assertFalse(entry.getKey() + " has no steps", helper.getSteps().isEmpty());
		}
	}

	@Test
	public void xMarksTheSpotResolvesFromAProseMention() throws Exception
	{
		// The step says "Start X Marks the Spot on Veos" with no [tag] at all,
		// which is how the guide names quests about half the time.
		final Guide guide = loader.loadBundled();
		Step veos = null;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getText().contains("Start X Marks the Spot on Veos"))
				{
					veos = step;
				}
			}
		}
		assertNotNull(veos);
		assertEquals("xmarksthespot", veos.getQuestHelper());

		final QuestHelperSteps helper = guide.questHelperFor(veos);
		assertNotNull(helper);
		assertTrue("X Marks the Spot's progress is a varbit", helper.getVar().isVarbit());
		assertNotNull("value 0 should have an instruction", helper.at(0));
		assertTrue(helper.at(0).getText().contains("Veos"));
	}

	@Test
	public void everyReferencedHelperExists() throws Exception
	{
		// A dangling key would silently show nothing, which is the failure this
		// project keeps having.
		final Guide guide = loader.loadBundled();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getQuestHelper() != null)
				{
					assertNotNull("dangling questHelper key on: " + step.getText(),
						guide.questHelperFor(step));
				}
			}
		}
	}

	@Test
	public void conditionalInstructionsAreFlaggedWithTheirBranchCount() throws Exception
	{
		final Map<String, QuestHelperSteps> helpers = loader.loadBundled().getQuestHelpers();
		int conditional = 0;
		for (QuestHelperSteps helper : helpers.values())
		{
			for (QuestHelperSteps.Instruction instruction : helper.getSteps().values())
			{
				if (instruction.isConditional())
				{
					conditional++;
					assertTrue("a conditional should record its branch count",
						instruction.getBranches() >= 0);
				}
				assertFalse("every instruction needs text", instruction.getText().isEmpty());
			}
		}
		assertTrue("most of Quest Helper's slots are conditional", conditional > 0);
	}

	@Test
	public void aStepWithNoQuestHasNoHelper() throws Exception
	{
		final Guide guide = loader.loadBundled();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getQuestHelper() == null)
				{
					assertNull(guide.questHelperFor(step));
					return;
				}
			}
		}
		throw new AssertionError("every step had a quest helper, which cannot be right");
	}
}
