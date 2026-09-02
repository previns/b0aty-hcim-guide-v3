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
 * Quest Helper's instructions for one quest, keyed by that quest's own progress
 * value.
 *
 * <p>The guide advances a quest a step or two in passing rather than doing it in
 * one sitting, so a player following it needs to know what the next step of the
 * quest is at whatever value they are at. Reading the quest's varbit gives that
 * directly.
 */
public class QuestHelperSteps
{
	public static class Var
	{
		private String kind;
		private int id;
		private String constant;

		/** "varbit" or "varplayer" -- the two places quest progress is stored. */
		public String getKind()
		{
			return kind;
		}

		public int getId()
		{
			return id;
		}

		public String getConstant()
		{
			return constant;
		}

		public boolean isVarbit()
		{
			return "varbit".equals(kind);
		}
	}

	public static class Instruction
	{
		private String kind;
		private String constant;
		private List<Integer> point;
		private String text;
		private boolean conditional;
		private int branches;

		/** "npc", "object" or "walk". */
		public String getKind()
		{
			return kind;
		}

		public String getConstant()
		{
			return constant;
		}

		public List<Integer> getPoint()
		{
			return point == null ? Collections.emptyList() : point;
		}

		public String getText()
		{
			return text == null ? "" : text;
		}

		/**
		 * True when this is a Quest Helper ConditionalStep's <em>default</em>
		 * branch rather than the branch its conditions would pick.
		 *
		 * <p>Reproducing those conditions means porting Quest Helper's
		 * requirement engine, which this plugin deliberately does not do. The
		 * default is right when you arrive at a step and can be stale once you
		 * are partway through it, so it must be shown as a hint and never as
		 * certainty.
		 */
		public boolean isConditional()
		{
			return conditional;
		}

		/** How many branches the real conditional had. More means less certain. */
		public int getBranches()
		{
			return branches;
		}
	}

	private Var var;
	private Map<String, Instruction> steps;

	public Var getVar()
	{
		return var;
	}

	public Map<String, Instruction> getSteps()
	{
		return steps == null ? Collections.emptyMap() : steps;
	}

	/** The instruction at this progress value, or null when there is none. */
	public Instruction at(int value)
	{
		return getSteps().get(Integer.toString(value));
	}
}
