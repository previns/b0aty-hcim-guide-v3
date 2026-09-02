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
