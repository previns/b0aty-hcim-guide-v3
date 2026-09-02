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
import com.b0atyguide.data.Teleport;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The item a step travels with.
 *
 * <p>"Ardy Cloak -&gt; CKS" names the method as well as the destination, and
 * knowing which item to reach for is half the instruction -- the cloak has five
 * tiers and the step never says which one you own.
 */
public class TeleportTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static List<Step> withTeleport(Guide guide)
	{
		final List<Step> found = new ArrayList<>();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getTeleport() != null)
				{
					found.add(step);
				}
			}
		}
		return found;
	}

	@Test
	public void theGuideCarriesTravelMethods() throws Exception
	{
		final List<Step> steps = withTeleport(loader.loadBundled());
		assertTrue("expected plenty of teleport steps", steps.size() > 50);
		for (Step step : steps)
		{
			assertFalse("a teleport with no method is meaningless: " + step.getText(),
				step.getTeleport().getVia().isEmpty());
		}
	}

	@Test
	public void anArdougneCloakCarriesEveryTier() throws Exception
	{
		Step step = null;
		for (Step candidate : withTeleport(loader.loadBundled()))
		{
			if ("Ardy Cloak".equals(candidate.getTeleport().getVia()))
			{
				step = candidate;
				break;
			}
		}
		assertNotNull(step);
		final Teleport teleport = step.getTeleport();
		assertTrue(teleport.isItem());
		// Four diary capes plus the max cape: whichever the player owns lights up.
		assertTrue("expected every cloak tier", teleport.getIds().size() >= 4);
	}

	@Test
	public void aMethodThatIsNotAnItemStillNamesItself() throws Exception
	{
		// A fairy ring or a minecart has no item to ring, but "how do I get
		// there" is still worth showing.
		Step step = null;
		for (Step candidate : withTeleport(loader.loadBundled()))
		{
			if (!candidate.getTeleport().isItem())
			{
				step = candidate;
				break;
			}
		}
		assertNotNull("expected some non-item methods", step);
		assertFalse(step.getTeleport().getVia().isEmpty());
		assertTrue(step.getTeleport().getIds().isEmpty());
	}

	@Test
	public void everyResolvedMethodHasRealItemIds() throws Exception
	{
		for (Step step : withTeleport(loader.loadBundled()))
		{
			for (Integer id : step.getTeleport().getIds())
			{
				assertTrue("item id " + id + " on: " + step.getTeleport().getVia(), id > 0);
			}
		}
	}

	@Test
	public void mostStepsDoNotTeleport() throws Exception
	{
		final Guide guide = loader.loadBundled();
		assertTrue("travelling is the exception, not the rule",
			withTeleport(guide).size() < guide.getStepCount() / 10);
	}
}
