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
	 * Tick every step whose skill target has been reached.
	 *
	 * <p>As exact as the diary bits: the real level is a number the game keeps,
	 * not something inferred. Deciding which steps have a real target is the
	 * part that needed a person -- a boosted level is never reached, and
	 * "70 prayer is banked" is experience rather than a level.
	 *
	 * @param level reads a real skill level by name; unknown names give 0
	 * @return how many steps were newly ticked
	 */
	public static int skills(Guide guide, Progress progress, ToIntFunction<String> level)
	{
		if (guide == null)
		{
			return 0;
		}

		int ticked = 0;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (progress.isComplete(step.getId()))
				{
					continue;
				}
				final Completion completion = step.getCompletion();
				if (completion != null && completion.isSkill()
					&& level.applyAsInt(completion.getSkill()) >= completion.getLevel())
				{
					progress.setComplete(step.getId(), true);
					ticked++;
				}
			}
		}
		return ticked;
	}

	/**
	 * Tick every diary step whose bit is set.
	 *
	 * @param varp reads a VarPlayer by id; must not run a client script
	 * @return how many steps were newly ticked
	 */
	public static int diaries(Guide guide, Progress progress, IntUnaryOperator varp)
	{
		if (guide == null)
		{
			return 0;
		}

		int ticked = 0;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (progress.isComplete(step.getId()))
				{
					continue;
				}
				final Completion completion = step.getCompletion();
				if (completion != null && completion.isDiary()
					&& completion.isSetIn(varp.applyAsInt(completion.getVarplayer())))
				{
					progress.setComplete(step.getId(), true);
					ticked++;
				}
			}
		}
		return ticked;
	}
}
