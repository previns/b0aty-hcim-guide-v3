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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A bank in the guide -- one heading and the steps under it.
 *
 * <p>Numbered banks can repeat: a continuation section carries the previous
 * bank's number on purpose, so six banks span two sections.
 */
public class Section
{
	private String id;
	private String slug;
	private int ordinal;
	private String title;
	private Integer bankNumber;
	private String bankSuffix;
	private Integer episodeOrdinal;
	private String continuationOf;
	private List<String> imageUrls;
	private List<Step> steps;

	public String getId()
	{
		return id;
	}

	public String getSlug()
	{
		return slug;
	}

	public int getOrdinal()
	{
		return ordinal;
	}

	public String getTitle()
	{
		return title;
	}

	public Integer getBankNumber()
	{
		return bankNumber;
	}

	public String getBankSuffix()
	{
		return bankSuffix;
	}

	public Integer getEpisodeOrdinal()
	{
		return episodeOrdinal;
	}

	public String getContinuationOf()
	{
		return continuationOf;
	}

	public boolean isContinuation()
	{
		return continuationOf != null;
	}

	/**
	 * Take another section's steps and pictures as the rest of this bank.
	 *
	 * <p>The wiki splits a bank in two when something sits between the halves --
	 * a linked video, usually -- and the build records that faithfully, with the
	 * second half pointing back through {@code continuationOf}. Nothing read it,
	 * so six banks appeared twice in the sidebar, and crossing from one half to
	 * the other looked like arriving at a new bank.
	 *
	 * <p>Merged here rather than in the pipeline on purpose. A step's id is
	 * derived from its section's slug, so merging upstream would rewrite the ids
	 * of all 31 steps in those second halves -- and the migration map is built
	 * per slug, so a slug that stops existing produces no migrations at all.
	 * Every one of those steps would have come back unticked.
	 */
	void absorb(Section rest)
	{
		final List<Step> mine = new ArrayList<>(getSteps());
		int position = mine.isEmpty() ? 0 : mine.get(mine.size() - 1).getOrdinal() + 1;
		for (Step step : rest.getSteps())
		{
			step.renumber(position++);
			mine.add(step);
		}
		steps = mine;

		if (!rest.getImageUrls().isEmpty())
		{
			final List<String> pictures = new ArrayList<>(getImageUrls());
			pictures.addAll(rest.getImageUrls());
			imageUrls = pictures;
		}
	}

	public List<String> getImageUrls()
	{
		return imageUrls == null ? Collections.emptyList() : imageUrls;
	}

	public List<Step> getSteps()
	{
		return steps == null ? Collections.emptyList() : steps;
	}

	/**
	 * "Bank 39A" for a numbered bank, otherwise the section's own title.
	 * Continuation sections repeat the previous bank's number by design, so
	 * they render with the same label.
	 */
	public String getDisplayLabel()
	{
		if (bankNumber == null)
		{
			return title;
		}
		return "Bank " + bankNumber + (bankSuffix == null ? "" : bankSuffix);
	}
}
