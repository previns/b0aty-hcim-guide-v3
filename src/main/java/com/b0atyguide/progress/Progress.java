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
package com.b0atyguide.progress;

import com.b0atyguide.data.Guide;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which steps a player has ticked off.
 *
 * <p>Pure data plus the migration rule. Nothing here touches the client or
 * config, so all of it is testable without a game running -- which matters,
 * because this is the only part of the plugin that can destroy something the
 * player cannot get back.
 */
public class Progress
{
	private final Set<String> completed;

	/**
	 * The subset of {@link #completed} the player ticked one at a time.
	 *
	 * <p>Kept separately so that "complete this bank" is reversible without
	 * destroying real work: un-completing a bank drops everything it ticked in
	 * bulk and keeps everything the player had already ticked themselves. Press
	 * the bank checkbox by accident and nothing is lost.
	 */
	private final Set<String> manual;

	public Progress()
	{
		this(Collections.emptySet(), Collections.emptySet());
	}

	public Progress(Set<String> completed, Set<String> manual)
	{
		this.completed = new LinkedHashSet<>(completed);
		// A manual id that is not completed is meaningless, and could only come
		// from hand-edited config.
		this.manual = new LinkedHashSet<>(manual);
		this.manual.retainAll(this.completed);
	}

	public boolean isManual(String stepId)
	{
		return manual.contains(stepId);
	}

	public Set<String> manualIds()
	{
		return Collections.unmodifiableSet(manual);
	}

	/**
	 * Tick or untick every step in a section.
	 *
	 * <p>Completing marks nothing as manual. Un-completing keeps the steps the
	 * player ticked individually, which is what makes an accidental press
	 * harmless.
	 *
	 * @return true when anything changed
	 */
	public boolean setSectionComplete(Section section, boolean complete)
	{
		boolean changed = false;
		for (Step step : section.getSteps())
		{
			final String id = step.getId();
			if (complete)
			{
				changed |= completed.add(id);
			}
			else if (!manual.contains(id))
			{
				changed |= completed.remove(id);
			}
		}
		return changed;
	}

	/** How many steps in this section would survive un-completing the bank. */
	public int manualIn(Section section)
	{
		int count = 0;
		for (Step step : section.getSteps())
		{
			if (manual.contains(step.getId()))
			{
				count++;
			}
		}
		return count;
	}

	public boolean isComplete(String stepId)
	{
		return completed.contains(stepId);
	}

	/** Ticking a single step records it as manual; unticking forgets that. */
	public void setComplete(String stepId, boolean complete)
	{
		if (complete)
		{
			completed.add(stepId);
			manual.add(stepId);
		}
		else
		{
			completed.remove(stepId);
			manual.remove(stepId);
		}
	}

	public Set<String> completedIds()
	{
		return Collections.unmodifiableSet(completed);
	}

	public int count()
	{
		return completed.size();
	}

	public void clear()
	{
		completed.clear();
		manual.clear();
	}

	/**
	 * Rewrite saved ids through the guide's migration map.
	 *
	 * <p>When an editor rewords a step its id changes, and without this a
	 * player's tick silently vanishes. The map is applied transitively so
	 * somebody returning after several releases still lands on the current id.
	 *
	 * @return how many ids were rewritten
	 */
	public int applyMigrations(Map<String, String> migrations)
	{
		if (migrations.isEmpty() || completed.isEmpty())
		{
			return 0;
		}

		final Set<String> migrated = new LinkedHashSet<>();
		int changed = 0;

		for (String id : completed)
		{
			final String current = follow(migrations, id);
			if (!current.equals(id))
			{
				changed++;
			}
			migrated.add(current);
		}

		final Set<String> migratedManual = new LinkedHashSet<>();
		for (String id : manual)
		{
			migratedManual.add(follow(migrations, id));
		}

		completed.clear();
		completed.addAll(migrated);
		manual.clear();
		manual.addAll(migratedManual);
		manual.retainAll(completed);
		return changed;
	}

	/**
	 * Walk a migration chain to the current id.
	 *
	 * <p>Bounded by the ids already visited, so a cycle in the map returns
	 * rather than hanging the client on startup.
	 */
	private static String follow(Map<String, String> migrations, String id)
	{
		String current = id;
		final Set<String> visited = new LinkedHashSet<>();
		visited.add(current);
		String next = migrations.get(current);
		while (next != null && visited.add(next))
		{
			current = next;
			next = migrations.get(current);
		}
		return current;
	}

	/**
	 * Drop ticks for steps the guide no longer contains.
	 *
	 * <p>Run after {@link #applyMigrations}. A step that was deleted outright
	 * is gone; keeping its id would let saved data grow without bound and would
	 * make the completion counts wrong.
	 *
	 * @return how many stale ids were dropped
	 */
	public int prune(Guide guide)
	{
		final Set<String> known = new LinkedHashSet<>();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				known.add(step.getId());
			}
		}
		final int before = completed.size();
		completed.retainAll(known);
		manual.retainAll(known);
		return before - completed.size();
	}

	public int completedIn(Section section)
	{
		int done = 0;
		for (Step step : section.getSteps())
		{
			if (completed.contains(step.getId()))
			{
				done++;
			}
		}
		return done;
	}

	public boolean isSectionComplete(Section section)
	{
		return !section.getSteps().isEmpty()
			&& completedIn(section) == section.getSteps().size();
	}

	/** The first step the player has not ticked, or null when the guide is done. */
	public Step firstIncompleteStep(Guide guide)
	{
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (!completed.contains(step.getId()))
				{
					return step;
				}
			}
		}
		return null;
	}

	public Section sectionOf(Guide guide, Step target)
	{
		if (target == null)
		{
			return null;
		}
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getId().equals(target.getId()))
				{
					return section;
				}
			}
		}
		return null;
	}
}
