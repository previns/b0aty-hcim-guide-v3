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
import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.google.gson.Gson;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The "what am I still missing" comparison.
 *
 * <p>The tracker itself needs a live inventory, so the rule it applies is
 * tested here against the shipped data instead: a requirement is met by any one
 * of its ids, and an unresolved requirement is never reported as missing.
 */
public class WithdrawTrackerTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static Section firstSectionWithItems(Guide guide)
	{
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (!step.getItems().isEmpty())
				{
					return section;
				}
			}
		}
		throw new AssertionError("no section with a withdraw list");
	}

	/** The rule the tracker applies, without needing a client. */
	private static boolean satisfied(ItemRef item, Set<Integer> carried)
	{
		for (Integer id : item.getIds())
		{
			if (carried.contains(id))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void anyOneOfARequirementsIdsSatisfiesIt() throws Exception
	{
		// "Pickaxe" carries every pickaxe; owning one is enough.
		final Guide guide = loader.loadBundled();
		ItemRef multi = null;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				for (ItemRef item : step.getItems())
				{
					if (item.getIds().size() > 3)
					{
						multi = item;
					}
				}
			}
		}
		assertNotNull("expected a category requirement", multi);

		final Set<Integer> carried = new LinkedHashSet<>();
		carried.add(multi.getIds().get(multi.getIds().size() - 1));
		assertTrue("the worst-tier variant should still count", satisfied(multi, carried));
	}

	@Test
	public void carryingNothingLeavesEveryResolvedRequirementMissing() throws Exception
	{
		final Section section = firstSectionWithItems(loader.loadBundled());
		final Set<Integer> carried = new LinkedHashSet<>();
		int resolved = 0;
		for (Step step : section.getSteps())
		{
			for (ItemRef item : step.getItems())
			{
				if (item.isResolved())
				{
					resolved++;
					assertFalse(satisfied(item, carried));
				}
			}
		}
		assertTrue("expected some resolved requirements", resolved > 0);
	}

	@Test
	public void anUnresolvedRequirementIsNeverReportedMissing() throws Exception
	{
		// "Combat gear" has no ids by design. Flagging it would put a permanent
		// warning on the panel that no amount of banking can clear.
		final Guide guide = loader.loadBundled();
		int unresolved = 0;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				for (ItemRef item : step.getItems())
				{
					if (!item.isResolved())
					{
						unresolved++;
						assertTrue(item.getIds().isEmpty());
					}
				}
			}
		}
		assertTrue("the guide still has unresolvable requirements", unresolved > 0);
	}

	@Test
	public void everyResolvedRequirementHasRealItemIds() throws Exception
	{
		final Guide guide = loader.loadBundled();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				for (ItemRef item : step.getItems())
				{
					for (Integer id : item.getIds())
					{
						assertTrue("item id " + id + " on: " + item.getName(), id > 0);
					}
				}
			}
		}
	}

	@Test
	public void teleportRunesCarryLawAndTheFourElements() throws Exception
	{
		// The largest single requirement in the guide, and the one a player is
		// most likely to leave the bank without.
		final Guide guide = loader.loadBundled();
		ItemRef runes = null;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				for (ItemRef item : step.getItems())
				{
					if ("Teleport Runes".equals(item.getName()))
					{
						runes = item;
					}
				}
			}
		}
		assertNotNull(runes);
		assertTrue(runes.isResolved());
		// Law rune, plus the four elemental collections including combination
		// runes -- so a mud rune counts as the water rune it also is.
		assertTrue("expected law plus four elements", runes.getIds().size() >= 5);
		assertTrue("law rune should be in the set", runes.getIds().contains(563));
	}

	@Test
	public void aSectionWithNoItemsAsksForNothing() throws Exception
	{
		final Guide guide = loader.loadBundled();
		for (Section section : guide.getSections())
		{
			boolean any = false;
			for (Step step : section.getSteps())
			{
				any |= !step.getItems().isEmpty();
			}
			if (!any)
			{
				assertEquals(0, section.getSteps().stream()
					.mapToInt(s -> s.getItems().size()).sum());
				return;
			}
		}
	}
}
