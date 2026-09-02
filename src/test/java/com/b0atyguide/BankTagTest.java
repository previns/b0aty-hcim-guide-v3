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

import com.b0atyguide.bank.GuideBankTags;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Bank tag naming and membership, which are pure functions of the guide.
 * Whether the game's bank search actually finds a registered tag needs a
 * client and is checked by hand.
 */
public class BankTagTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	@Test
	public void tagNamesAreWhatAPlayerWouldType() throws Exception
	{
		final Guide guide = loader.loadBundled();
		Section bankOne = null;
		for (Section section : guide.getSections())
		{
			if (Integer.valueOf(1).equals(section.getBankNumber()))
			{
				bankOne = section;
				break;
			}
		}
		assertNotNull(bankOne);
		assertEquals("bank1", GuideBankTags.tagFor(bankOne));
	}

	@Test
	public void letterSuffixedBanksKeepTheirLetter() throws Exception
	{
		// Banks 39, 105 and 164 exist only as A/B variants; a tag of "bank39"
		// for both would collide and show one bank's items under the other's.
		final Guide guide = loader.loadBundled();
		final Set<String> names = new HashSet<>();
		int suffixed = 0;
		for (Section section : guide.getSections())
		{
			final String name = GuideBankTags.tagFor(section);
			if (name == null)
			{
				continue;
			}
			if (section.getBankSuffix() != null)
			{
				suffixed++;
				assertTrue(name + " should carry its suffix",
					name.endsWith(section.getBankSuffix().toLowerCase()));
			}
			names.add(name);
		}
		assertTrue("the guide has letter-suffixed banks", suffixed > 0);
	}

	@Test
	public void tagsAreLowercaseSoBankSearchFindsThem() throws Exception
	{
		for (Section section : loader.loadBundled().getSections())
		{
			final String name = GuideBankTags.tagFor(section);
			if (name != null)
			{
				assertEquals(name, name.toLowerCase());
			}
		}
	}

	@Test
	public void aSectionWithNoBankNumberGetsNoTag() throws Exception
	{
		// The preamble and titled asides. There is nothing a player would type.
		final Guide guide = loader.loadBundled();
		int untagged = 0;
		for (Section section : guide.getSections())
		{
			if (section.getBankNumber() == null)
			{
				assertNull(GuideBankTags.tagFor(section));
				untagged++;
			}
		}
		assertTrue(untagged > 0);
	}

	@Test
	public void aBanksItemsIncludeEveryIdBehindACategoryWord() throws Exception
	{
		// "Pickaxe" resolves to every pickaxe, so whichever the player owns is
		// the one that shows. A single id would show an empty tab.
		final Guide guide = loader.loadBundled();
		int withItems = 0;
		int biggest = 0;
		for (Section section : guide.getSections())
		{
			final Set<Integer> ids = GuideBankTags.itemIds(section);
			if (!ids.isEmpty())
			{
				withItems++;
				biggest = Math.max(biggest, ids.size());
			}
		}
		assertTrue("most banks should have a withdraw list", withItems > 100);
		assertTrue("category words should expand to many ids", biggest > 20);
	}

	@Test
	public void itemIdsNeverContainZeroOrNegative() throws Exception
	{
		for (Section section : loader.loadBundled().getSections())
		{
			for (Integer id : GuideBankTags.itemIds(section))
			{
				assertTrue("item id " + id + " is not a real item", id > 0);
			}
		}
	}

	@Test
	public void anEmptySectionContributesNothing() throws Exception
	{
		final Guide guide = loader.loadBundled();
		for (Section section : guide.getSections())
		{
			if (section.getSteps().isEmpty())
			{
				assertTrue(GuideBankTags.itemIds(section).isEmpty());
			}
		}
		assertFalse(guide.getSections().isEmpty());
	}

	@Test
	public void bothSpellingsOfABankAreRegistered() throws Exception
	{
		// Bank Tags looks a custom tag up by exact name, so "bank 118" found
		// nothing when only "bank118" existed -- and the guide writes it with
		// the space.
		final Guide guide = loader.loadBundled();
		Section bank = null;
		for (Section section : guide.getSections())
		{
			if (Integer.valueOf(118).equals(section.getBankNumber()))
			{
				bank = section;
				break;
			}
		}
		assertNotNull(bank);

		final List<String> names = GuideBankTags.tagNamesFor(bank);
		assertTrue(names.contains("bank118"));
		assertTrue(names.contains("bank 118"));
	}

	@Test
	public void aSplitBanksSectionsShareOneTagName() throws Exception
	{
		// Six banks run across two sections, because a continuation repeats the
		// previous bank's number. They must share a tag rather than each get
		// their own, or "bank 30" shows half of what bank 30 needs.
		final Guide guide = loader.loadBundled();
		final Map<String, Integer> uses = new HashMap<>();
		for (Section section : guide.getSections())
		{
			for (String name : GuideBankTags.tagNamesFor(section))
			{
				uses.merge(name, 1, Integer::sum);
			}
		}
		int shared = 0;
		for (Integer count : uses.values())
		{
			if (count > 1)
			{
				shared++;
			}
		}
		assertTrue("the guide has banks split across sections", shared > 0);
		assertFalse(uses.isEmpty());
	}

	@Test
	public void aSectionWithNoBankNumberYieldsNoNames() throws Exception
	{
		for (Section section : loader.loadBundled().getSections())
		{
			if (section.getBankNumber() == null)
			{
				assertTrue(GuideBankTags.tagNamesFor(section).isEmpty());
				return;
			}
		}
	}
}
