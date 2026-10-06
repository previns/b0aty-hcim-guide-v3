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
import com.b0atyguide.data.Section;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native bank filtering over a guide snapshot, without touching saved item tags. */
public final class GuideBankSearch
{
	private static final Pattern QUERY = Pattern.compile("bank\\s*#?\\s*([0-9]+[a-z]?)", Pattern.CASE_INSENSITIVE);
	private final Map<String, Set<Integer>> items = new HashMap<>();
	// Bank search calls once per item. Resolve the query once, on the client thread.
	private String lastQuery;
	private Set<Integer> lastItems;

	public GuideBankSearch(Guide guide)
	{
		if (guide == null) { return; }
		for (Section section : guide.getSections())
		{
			String name = GuideBankTags.tagFor(section);
			if (name != null)
			{
				items.computeIfAbsent(name, key -> new HashSet<>()).addAll(GuideBankTags.itemIds(section));
			}
		}
	}

	/** Null means an unrelated query, which must keep the existing search result. */
	public Boolean matches(String query, int itemId)
	{
		if (query == null) { return null; }
		if (!query.equals(lastQuery))
		{
			lastQuery = query;
			Matcher match = QUERY.matcher(query.trim());
			lastItems = match.matches()
				? items.get("bank" + match.group(1).toLowerCase(Locale.ROOT)) : null;
		}
		return lastItems == null ? null : lastItems.contains(itemId);
	}

	/** The native bankSearchFilter stack contract: result, item id; query string. */
	public void filter(int[] integers, int integerSize, Object[] objects, int objectSize)
	{
		if (integers == null || objects == null || integerSize < 2 || integerSize > integers.length
			|| objectSize < 1 || objectSize > objects.length || !(objects[objectSize - 1] instanceof String))
		{
			return;
		}
		// Layout plugins use -1 as a draggable empty slot, not a bank item.
		if (integers[integerSize - 1] < 0) { return; }
		Boolean match = matches((String) objects[objectSize - 1], integers[integerSize - 1]);
		if (match != null)
		{
			integers[integerSize - 2] = match ? 1 : 0;
		}
	}
}
