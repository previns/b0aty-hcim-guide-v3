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
package com.b0atyguide.bank;

import com.b0atyguide.data.Guide;
import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.banktags.TagManager;

/**
 * Makes each bank's withdraw list searchable in game: type {@code bank150} in
 * the bank and the items that bank needs are what you see.
 *
 * <p>The tags are <b>virtual</b>. {@link TagManager#registerTag} takes a
 * predicate, so membership is computed from the guide rather than written onto
 * the player's items. That matters for three reasons: the player's own bank
 * tags are never touched, uninstalling leaves nothing behind, and a wiki edit
 * changes what a tag contains with no migration and no stale config. Writing
 * ~1,300 {@code item_<id>} keys into their profile would fail all three.
 *
 * <p>Degrades to nothing if the Bank Tags plugin is off: the tag is registered
 * and simply never consulted.
 */
@Slf4j
@Singleton
public class GuideBankTags
{
	/**
	 * Lower-cased because the bank's search box is, and a tag that only matches
	 * when capitalised is a tag nobody finds.
	 */
	private static final String PREFIX = "bank";

	@Inject
	private TagManager tagManager;

	private final List<String> registered = new ArrayList<>();

	public void register(Guide guide)
	{
		clear();
		if (guide == null)
		{
			return;
		}

		// Six banks are split across two sections -- a continuation repeats the
		// previous bank's number by design -- so the ids are gathered per tag
		// name first. Registering per section instead let the second overwrite
		// the first, and "bank 30" then showed only half of what bank 30 needs.
		final Map<String, Set<Integer>> byName = new LinkedHashMap<>();
		for (Section section : guide.getSections())
		{
			final Set<Integer> ids = itemIds(section);
			if (ids.isEmpty())
			{
				continue;
			}
			for (String name : tagNamesFor(section))
			{
				byName.computeIfAbsent(name, key -> new LinkedHashSet<>()).addAll(ids);
			}
		}

		for (Map.Entry<String, Set<Integer>> entry : byName.entrySet())
		{
			// A snapshot, not a live view: the guide is replaced wholesale on
			// reload, and a tag still holding the old Section would quietly
			// describe a bank that no longer exists.
			final Set<Integer> snapshot = Collections.unmodifiableSet(entry.getValue());
			tagManager.registerTag(entry.getKey(), snapshot::contains);
			registered.add(entry.getKey());
		}
		log.debug("registered {} bank tags", registered.size());
	}

	public void clear()
	{
		for (String name : registered)
		{
			tagManager.unregisterTag(name);
		}
		registered.clear();
	}

	public List<String> registeredTags()
	{
		return Collections.unmodifiableList(registered);
	}

	/**
	 * "bank39a" for Bank 39A. Sections with no bank number -- the preamble and
	 * the odd titled aside -- get no tag: there is nothing a player would type
	 * to reach them.
	 */
	public static String tagFor(Section section)
	{
		final Integer number = section.getBankNumber();
		if (number == null)
		{
			return null;
		}
		final String suffix = section.getBankSuffix();
		return (PREFIX + number + (suffix == null ? "" : suffix)).toLowerCase(Locale.ROOT);
	}

	/**
	 * Every spelling of a bank's tag a player might type.
	 *
	 * <p>Bank Tags looks a custom tag up by exact name, so "bank 118" finds
	 * nothing when only "bank118" is registered -- and the guide itself writes
	 * the number with a space. Both point at the same set.
	 *
	 * <p>That exact lookup is also why there is no prefix problem to solve:
	 * "bank1" cannot pull in bank10 or bank120, because nothing here is ever
	 * compared by prefix.
	 */
	public static List<String> tagNamesFor(Section section)
	{
		final String compact = tagFor(section);
		if (compact == null)
		{
			return Collections.emptyList();
		}
		final String spaced = PREFIX + " " + compact.substring(PREFIX.length());
		return Arrays.asList(compact, spaced);
	}

	/**
	 * Every item id the bank's steps name.
	 *
	 * <p>Includes all ids behind a category word: "Pickaxe" carries every
	 * pickaxe, so whichever one the player owns is the one that shows.
	 */
	public static Set<Integer> itemIds(Section section)
	{
		final Set<Integer> ids = new LinkedHashSet<>();
		for (Step step : section.getSteps())
		{
			for (ItemRef item : step.getItems())
			{
				ids.addAll(item.getIds());
			}
		}
		return ids;
	}
}
