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
package com.b0atyguide.overlay;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;

/**
 * Which of several identical objects the player has already tried.
 *
 * <p>"Search the roots under the Grand Tree until you find the Daconia stone"
 * is one instruction pointing at a dozen roots. Outlining all of them is right
 * on the first search and useless on the seventh: the player is left counting
 * from memory which ones they have already opened.
 *
 * <p>So a root the player has searched stops being outlined, and what is left
 * lit is what is left to try.
 *
 * <h2>Why this is not driven by the step's words</h2>
 *
 * <p>Six Quest Helper instructions say "until you find", and reading that as
 * permission would break two of them. The Digsite's is a single dig spot dug
 * repeatedly, and the Witch's House's is one gate; dimming either leaves the
 * player with nothing highlighted and no idea why.
 *
 * <p>The gate is arithmetic instead, and it is self-limiting: dimming only ever
 * happens while <em>several</em> matches are on screen. One dig spot and one
 * gate are never several, so those steps cannot be affected by this at all.
 *
 * <p>And when the last candidate would go dark, the memory is cleared instead.
 * The stone is in one of those roots; a player who searched them all and found
 * nothing has miscounted, mis-clicked, or been interrupted, and showing them
 * everything again is a better answer than showing them nothing.
 */
@Singleton
public class SearchedObjects
{
	private final Set<WorldPoint> tried = new HashSet<>();

	/** Remember that this tile has been tried. */
	public void searched(WorldPoint at)
	{
		if (at != null)
		{
			tried.add(at);
		}
	}

	public boolean isSearched(WorldPoint at)
	{
		return at != null && tried.contains(at);
	}

	public boolean isEmpty()
	{
		return tried.isEmpty();
	}

	/**
	 * Forget everything.
	 *
	 * <p>Called whenever the instruction changes, the step changes or the scene
	 * is rebuilt. Memory of a root is only meaningful for as long as the guide
	 * is still asking about roots.
	 */
	public void clear()
	{
		tried.clear();
	}
}
