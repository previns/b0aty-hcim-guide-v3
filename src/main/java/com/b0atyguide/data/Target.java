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
package com.b0atyguide.data;

import java.util.Collections;
import java.util.List;

/**
 * What a step points at.
 *
 * <p>{@code confidence} says where the identification came from, and callers
 * should respect it: only "linked", "wiki-exact" and "wiki-redirect" carry IDs
 * and coordinates confirmed against the wiki. An "inferred" target is a name
 * the extraction grammar guessed and nothing confirmed, so it has no IDs and
 * can only be matched against the loaded scene by name -- where a wrong guess
 * simply matches nothing.
 */
public class Target
{
	public static final String KIND_NPC = "npc";
	public static final String KIND_OBJECT = "object";
	public static final String KIND_ITEM = "item";
	public static final String KIND_PLACE = "place";

	private String name;
	private List<String> names;
	private String kind;
	private String wikiPage;
	private String confidence;
	private List<Integer> ids;
	private List<List<Integer>> points;

	public String getName()
	{
		return name;
	}

	/**
	 * Every name this target may match in the scene.
	 *
	 * <p>Usually one. Some steps point at a <em>kind</em> of NPC rather than a
	 * named one -- "Pickpocket a man/woman" means every Man and every Woman
	 * around -- and those carry several. {@link #getName} stays the label to
	 * show a human ("Man/Woman").
	 */
	public List<String> getNames()
	{
		return names == null || names.isEmpty()
			? Collections.singletonList(name)
			: names;
	}

	public String getKind()
	{
		return kind;
	}

	public String getWikiPage()
	{
		return wikiPage;
	}

	public String getConfidence()
	{
		return confidence;
	}

	public List<Integer> getIds()
	{
		return ids == null ? Collections.emptyList() : ids;
	}

	public List<List<Integer>> getPoints()
	{
		return points == null ? Collections.emptyList() : points;
	}

	public boolean isWikiBacked()
	{
		return "linked".equals(confidence)
			|| "wiki-exact".equals(confidence)
			|| "wiki-redirect".equals(confidence)
			|| "manual".equals(confidence);
	}

	public boolean isHighlightable()
	{
		return KIND_NPC.equals(kind) || KIND_OBJECT.equals(kind);
	}
}
