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

import com.b0atyguide.data.Completion;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * Ticking steps the game itself says are finished.
 *
 * <p>Split out from the plugin because of when each signal may be read.
 * Reading a VarPlayer is an array lookup and is safe anywhere. Asking for a
 * quest's state is not: {@code Quest.getState} runs a client script, and the
 * client refuses to run one from inside another. {@code VarbitChanged} is
 * posted from inside script execution, so a quest check there kills the client
 * thread with "scripts are not reentrant" -- which looks to the player like the
 * game hanging on "Please wait" at login.
 *
 * <p>Keeping the diary path pure also makes it testable without a client, which
 * matters more here than anywhere else in the plugin: this is the only code
 * that marks work done on the player's behalf.
 */
public final class AutoTick
{
	private AutoTick()
	{
	}

	/**
	 * Tick every step all of whose recorded conditions now hold.
	 *
	 * <p>One pass rather than one per signal, because a step can need several
	 * things at once. "Train Draynor Agility to 5 Agility [Lumbridge Easy
	 * Diary]" carries a diary bit and a level, and it is done only when both
	 * are true -- the Lumbridge task can be finished below level 5, so ticking
	 * on the bit alone marked training complete that the player still owed.
	 * Splitting this into a diaries() and a skills() pass is what let each one
	 * tick a step the other had not agreed to.
	 *
	 * <p>Both lookups are plain reads -- a VarPlayer is an array index, a real
	 * level is a number the game keeps -- so this is safe to call from inside a
	 * client script, unlike anything that asks for quest state.
	 *
	 * @param varp    reads a VarPlayer by id; must not run a client script
	 * @param level   reads a real, unboosted skill level by name
	 * @return how many steps were newly ticked
	 */
	public static int completions(Guide guide, Progress progress,
		IntUnaryOperator varp, ToIntFunction<String> level)
	{
		return completions(guide, progress, varp, level, (String) null);
	}

	public static int completions(Guide guide, Progress progress,
		IntUnaryOperator varp, ToIntFunction<String> level, String onlyKind)
	{
		if (guide == null)
		{
			return 0;
		}

		int count = 0;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (progress.isComplete(step.getId()))
				{
					continue;
				}
				final Completion completion = step.getCompletion();
				if (completion == null
					|| (onlyKind != null && !onlyKind.equals(completion.getKind())))
				{
					continue;
				}
				if (completion.isSatisfiedBy(varp, level))
				{
					progress.setComplete(step.getId(), true);
					count++;
				}
			}
		}
		return count;
	}
}
