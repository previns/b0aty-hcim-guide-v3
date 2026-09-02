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
import com.b0atyguide.data.Spell;
import com.b0atyguide.data.Step;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Which steps say to cast a spell.
 *
 * <p>The risk here is over-firing rather than missing: pointing at the Varrock
 * teleport on a step that says to use a tablet sends the player to the wrong
 * thing, and looks authoritative doing it.
 */
public class SpellTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	/** Anything that means a method other than casting the spellbook entry. */
	private static final Pattern OTHER_METHOD = Pattern.compile(
		"\\b(tab|tablet|teletab|necklace|cloak|ring|scroll|home\\s*teleport|portal|glory)\\b",
		Pattern.CASE_INSENSITIVE);

	private static List<Step> withSpell(Guide guide)
	{
		final List<Step> found = new ArrayList<>();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getSpell() != null)
				{
					found.add(step);
				}
			}
		}
		return found;
	}

	@Test
	public void theGuideCarriesSpellsToCast() throws Exception
	{
		final List<Step> steps = withSpell(loader.loadBundled());
		assertTrue("expected plenty of spell steps", steps.size() > 100);
		for (Step step : steps)
		{
			final Spell spell = step.getSpell();
			assertTrue("widget id must be real: " + step.getText(), spell.isUsable());
			assertNotNull(spell.getName());
		}
	}

	@Test
	public void everySpellStepIsPlainlyCastingOne() throws Exception
	{
		// The bug this guards: "Teleport to Falador with Teletab" pointed at
		// the Falador spell, and "Home teleport to Lumbridge" at the Lumbridge
		// one -- both a different method that merely ends up in the same place.
		for (Step step : withSpell(loader.loadBundled()))
		{
			final String text = step.getText();
			assertTrue("not a teleport instruction: " + text,
				text.toLowerCase(Locale.ROOT).startsWith("teleport to "));
			assertFalse("names a method that is not the spell: " + text,
				OTHER_METHOD.matcher(text).find());
		}
	}

	@Test
	public void aStepWithAnItemToUseHasNoSpell() throws Exception
	{
		// The two answer the same question. Showing both would say to cast a
		// spell and click a cloak for one teleport.
		for (Step step : withSpell(loader.loadBundled()))
		{
			assertTrue("a step should name one method, not two: " + step.getText(),
				step.getTeleport() == null);
		}
	}

	@Test
	public void mostStepsCastNothing() throws Exception
	{
		final Guide guide = loader.loadBundled();
		assertTrue("casting is the exception",
			withSpell(guide).size() < guide.getStepCount() / 10);
	}
}
