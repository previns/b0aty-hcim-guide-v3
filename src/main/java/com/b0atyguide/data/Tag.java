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

/**
 * A bracketed marker on a step -- "[Restless Ghost]", "[Lumbridge Easy Diary]".
 *
 * <p>A tag with a {@link #getConstant()} is one the client can verify. The
 * guide also brackets things that are not quests at all, and those resolve to
 * nothing on purpose.
 */
public class Tag
{
	private String tag;
	private String quest;
	private String constant;
	private String via;
	private boolean diary;

	public String getTag()
	{
		return tag;
	}

	/** RuneLite Quest display name, or null when the tag is not a quest. */
	public String getQuest()
	{
		return quest;
	}

	/** RuneLite Quest enum constant, e.g. THE_RESTLESS_GHOST. */
	public String getConstant()
	{
		return constant;
	}

	public String getVia()
	{
		return via;
	}

	public boolean isDiary()
	{
		return diary;
	}
}
