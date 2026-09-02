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
 * One line of the guide, and everything the pipeline could resolve about it.
 *
 * <p>{@link #getText()} is verbatim from the wiki and is the only field that
 * always exists. The rest are optional by design: most steps name no target, no
 * items and no destination, and an absent field means "not known", never "none".
 */
public class Step
{
	private String id;
	private int ordinal;
	private int depth;
	private String kind;
	private String text;
	private Integer inventorySlots;
	private Target target;
	private Completion completion;
	private Approach approach;
	private String questHelper;
	private boolean banking;
	private Teleport teleport;
	private Spell spell;
	private Destination destination;
	private List<ItemRef> items;
	private List<Tag> tags;
	private List<List<Integer>> dialogue;
	private List<String> videoIds;
	private List<String> urls;

	public String getId()
	{
		return id;
	}

	public int getOrdinal()
	{
		return ordinal;
	}

	/** 1 for a top-level step, 2 or 3 for the guide's nested sub-steps. */
	public int getDepth()
	{
		return depth < 1 ? 1 : depth;
	}

	public String getKind()
	{
		return kind;
	}

	public String getText()
	{
		return text == null ? "" : text;
	}

	public Integer getInventorySlots()
	{
		return inventorySlots;
	}

	public Target getTarget()
	{
		return target;
	}

	/** The spellbook entry to cast, or null. */
	public Spell getSpell()
	{
		return spell;
	}

	/** How the step says to travel, or null. */
	public Teleport getTeleport()
	{
		return teleport;
	}

	/**
	 * Whether this step sends the player to a bank.
	 *
	 * <p>From the verb the guide used, not a search for the word: "Keep the
	 * Shrimps in your bank for later" mentions one without being a trip to it.
	 */
	public boolean isBanking()
	{
		return banking;
	}

	/** Key into {@link Guide#getQuestHelpers()}, or null. */
	public String getQuestHelper()
	{
		return questHelper;
	}

	/** How to reach a target on another floor, or null. */
	public Approach getApproach()
	{
		return approach;
	}

	/** A completion the client can verify, or null for the great majority. */
	public Completion getCompletion()
	{
		return completion;
	}

	public Destination getDestination()
	{
		return destination;
	}

	public List<ItemRef> getItems()
	{
		return items == null ? Collections.emptyList() : items;
	}

	public List<Tag> getTags()
	{
		return tags == null ? Collections.emptyList() : tags;
	}

	public List<List<Integer>> getDialogue()
	{
		return dialogue == null ? Collections.emptyList() : dialogue;
	}

	public List<String> getVideoIds()
	{
		return videoIds == null ? Collections.emptyList() : videoIds;
	}

	public List<String> getUrls()
	{
		return urls == null ? Collections.emptyList() : urls;
	}

	/** A quest whose completion the client can actually verify, or null. */
	public Tag getVerifiableQuest()
	{
		for (Tag tag : getTags())
		{
			if (tag.getConstant() != null)
			{
				return tag;
			}
		}
		return null;
	}
}
