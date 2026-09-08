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

import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * A signal the game itself sets when a step is done.
 *
 * <p>Only present on steps where the client can <em>prove</em> completion.
 * Roughly 2,800 of the guide's steps ("Collect 3x Logs next to the stairs")
 * have no such signal and stay manual, which is deliberate: a step wrongly
 * ticked is silently skipped work, and the player finds out much later.
 */
public class Completion
{
	public static final String KIND_DIARY = "diary";
	public static final String KIND_SKILL = "skill";

	private String kind;
	private int varplayer;
	private String varplayerName;
	private int bit;
	private String skill;
	private int level;

	public String getKind()
	{
		return kind;
	}

	/**
	 * The VarPlayer id itself, not its name. Plugin Hub forbids reflection, so
	 * the number has to arrive in the data rather than be looked up at runtime.
	 */
	public int getVarplayer()
	{
		return varplayer;
	}

	/** The RuneLite constant name, for logs and for diffing the data. */
	public String getVarplayerName()
	{
		return varplayerName;
	}

	public int getBit()
	{
		return bit;
	}

	public boolean isDiary()
	{
		return KIND_DIARY.equals(kind) && bit >= 0 && bit <= 31;
	}

	/** The {@code net.runelite.api.Skill} name, or null when this is not a skill. */
	public String getSkill()
	{
		return skill;
	}

	/** The level that finishes the step. */
	public int getLevel()
	{
		return level;
	}

	public boolean isSkill()
	{
		return KIND_SKILL.equals(kind) && skill != null && level >= 1 && level <= 99;
	}

	/** Whether this bit is set in the value the client reports. */
	public boolean isSetIn(int varplayerValue)
	{
		return ((varplayerValue >> bit) & 1) == 1;
	}

	/**
	 * Whether every condition this step records is now true.
	 *
	 * <p>A step can carry more than one. "Train Draynor Agility to 5 Agility
	 * [Lumbridge Easy Diary]" needs the diary bit set <em>and</em> the level
	 * reached: the Lumbridge task can be finished below level 5, so ticking on
	 * the bit alone marked training complete that the player still had to do.
	 *
	 * <p>Conditions are ANDed, and a completion carrying none is never
	 * satisfied -- an empty rule must not tick a step.
	 *
	 * @param varplayer reads a VarPlayer by id; an array lookup, safe anywhere
	 * @param level     reads a real (unboosted) skill level by name
	 */
	public boolean isSatisfiedBy(IntUnaryOperator varplayer, ToIntFunction<String> level)
	{
		boolean any = false;

		if (hasDiaryCondition())
		{
			any = true;
			if (!isSetIn(varplayer.applyAsInt(this.varplayer)))
			{
				return false;
			}
		}

		if (hasSkillCondition())
		{
			any = true;
			if (level.applyAsInt(skill) < this.level)
			{
				return false;
			}
		}

		return any;
	}

	/** A diary task bit worth reading. */
	public boolean hasDiaryCondition()
	{
		return varplayer > 0 && bit >= 0 && bit <= 31;
	}

	/** A real skill level worth reading. */
	public boolean hasSkillCondition()
	{
		return skill != null && level >= 1 && level <= 99;
	}
}
