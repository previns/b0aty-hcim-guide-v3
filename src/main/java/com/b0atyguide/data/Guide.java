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
import java.util.Map;

/**
 * The whole guide, as loaded from guide.json.
 *
 * <p>Fields are populated by Gson via reflection, so they are deliberately
 * plain. Every collection accessor returns an empty list rather than null:
 * most steps carry none of the optional data, and callers should not have to
 * null-check on every render pass.
 */
public class Guide
{
	private int schemaVersion;
	private String sourcePage;
	private Integer sourceRevid;
	private String contentHash;
	private String generatedAt;
	private Map<String, String> migrations;
	private List<Episode> episodes;
	private String sourceRevisionAt;
	private List<Section> sections;
	private Map<String, QuestHelperSteps> questHelpers;
	private Map<String, QuestHelperSteps.Instruction> diaryTasks;
	private Map<String, String> imageHashes;
	private List<String> preamble;

	public int getSchemaVersion()
	{
		return schemaVersion;
	}

	public String getSourcePage()
	{
		return sourcePage;
	}

	public Integer getSourceRevid()
	{
		return sourceRevid;
	}

	public String getContentHash()
	{
		return contentHash;
	}

	public String getGeneratedAt()
	{
		return generatedAt;
	}

	public Map<String, String> getMigrations()
	{
		return migrations == null ? Collections.emptyMap() : migrations;
	}

	public List<Episode> getEpisodes()
	{
		return episodes == null ? Collections.emptyList() : episodes;
	}

	/**
	 * Quest Helper's step lists, stored once and referenced by
	 * {@link Step#getQuestHelper()}. Inlining them per step put 774 KiB of
	 * duplication into the shipped file.
	 */
	public Map<String, QuestHelperSteps> getQuestHelpers()
	{
		return questHelpers == null ? Collections.emptyMap() : questHelpers;
	}

	/**
	 * The sha256 a screenshot had when the guide was built, or null.
	 *
	 * <p>The host allowlist stops a link pointing at another server. This is
	 * what stops the picture behind an unchanged link being swapped afterwards
	 * -- the images live on a public host and their links come from a page
	 * anyone can edit.
	 */
	public String imageHashFor(String url)
	{
		return imageHashes == null ? null : imageHashes.get(url);
	}

	public QuestHelperSteps questHelperFor(Step step)
	{
		final String key = step == null ? null : step.getQuestHelper();
		return key == null ? null : getQuestHelpers().get(key);
	}

	/**
	 * What Quest Helper does for a diary task, keyed by the task's own bit.
	 *
	 * <p>A diary helper has no progress value to key on -- a task is done or it
	 * is not -- so it hangs each task off that task's varplayer bit. That is the
	 * same number this guide already carries in {@code completion}, read from
	 * the same constants, so the join is two integers against two integers.
	 */
	public Map<String, QuestHelperSteps.Instruction> getDiaryTasks()
	{
		return diaryTasks == null ? Collections.emptyMap() : diaryTasks;
	}

	public QuestHelperSteps.Instruction diaryTaskFor(Step step)
	{
		final String key = step == null ? null : step.getDiaryTask();
		return key == null ? null : getDiaryTasks().get(key);
	}

	/**
	 * The episode a section belongs to, or null.
	 *
	 * <p>The guide is a series of videos as much as a list of steps, and a
	 * player stuck on a bank often wants to watch that part rather than read
	 * it again.
	 */
	public Episode episodeFor(Section section)
	{
		if (section == null)
		{
			return null;
		}
		for (Episode episode : getEpisodes())
		{
			if (episode.getSectionIds().contains(section.getId()))
			{
				return episode;
			}
		}
		return null;
	}

	/**
	 * When the wiki page was last edited, ISO-8601, or null.
	 *
	 * <p>Not when the plugin was built: the guide ships with the client, so a
	 * player wants to know how current the *content* is. A page nobody has
	 * touched for three months is three months old however recently it was
	 * packaged.
	 */
	public String getSourceRevisionAt()
	{
		return sourceRevisionAt;
	}

	public List<Section> getSections()
	{
		return sections == null ? Collections.emptyList() : sections;
	}

	public List<String> getPreamble()
	{
		return preamble == null ? Collections.emptyList() : preamble;
	}

	public int getStepCount()
	{
		int total = 0;
		for (Section section : getSections())
		{
			total += section.getSteps().size();
		}
		return total;
	}

}
