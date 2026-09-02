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
import com.b0atyguide.data.Target;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Steps that point at a kind of NPC rather than a named one.
 *
 * <p>"Pickpocket a man/woman" means every Man and every Woman around. The
 * grammar cannot reach that -- lowercase, plural in meaning, split by a slash
 * -- so a person maps the phrase in curated/entity_aliases.yaml and the wiki
 * supplies every id.
 */
public class MultiNameTargetTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static List<Step> multiName(Guide guide)
	{
		final List<Step> found = new ArrayList<>();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				final Target target = step.getTarget();
				if (target != null && target.getNames().size() > 1)
				{
					found.add(step);
				}
			}
		}
		return found;
	}

	@Test
	public void aSingleNameTargetStillReportsOneName() throws Exception
	{
		final Guide guide = loader.loadBundled();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				final Target target = step.getTarget();
				if (target != null && target.getNames().size() == 1)
				{
					assertEquals(target.getName(), target.getNames().get(0));
					return;
				}
			}
		}
		throw new AssertionError("no single-name target in the guide");
	}

	@Test
	public void pickpocketingAManOrWomanMatchesBoth() throws Exception
	{
		final Guide guide = loader.loadBundled();
		Step pickpocket = null;
		for (Step step : multiName(guide))
		{
			if (step.getText().startsWith("Pickpocket a man/woman"))
			{
				pickpocket = step;
			}
		}
		assertNotNull("the man/woman step should carry both names", pickpocket);

		final Target target = pickpocket.getTarget();
		assertTrue(target.getNames().contains("Man"));
		assertTrue(target.getNames().contains("Woman"));
		// Every id the game uses for either, so any of them in the scene
		// highlights rather than only the one the wiki lists first.
		assertTrue("should carry every Man and Woman id", target.getIds().size() > 20);
	}

	@Test
	public void theLabelIsReadableForAHuman() throws Exception
	{
		for (Step step : multiName(loader.loadBundled()))
		{
			final Target target = step.getTarget();
			assertNotNull(target.getName());
			assertFalse(target.getName().isEmpty());
			// The panel and the step overlay show getName(), not the list.
			assertTrue(target.getName().contains("/") || target.getNames().size() == 1);
		}
	}

	@Test
	public void everyMultiNameTargetCarriesIds() throws Exception
	{
		// These come only from curated aliases, which are gated on the wiki
		// having resolved every named entity. A nameless or idless one means
		// the gate let something through.
		for (Step step : multiName(loader.loadBundled()))
		{
			assertFalse(step.getText(), step.getTarget().getIds().isEmpty());
		}
	}
}
