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
package com.b0atyguide.path;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/** Scene-scoped object transforms. Variable bursts trigger one small check per tick. */
public final class TransformState
{
	private final Map<Integer, Integer> definitions = new HashMap<>();
	private boolean pending;

	public void record(int rawId, int resolvedId)
	{
		definitions.put(rawId, resolvedId);
	}

	public void invalidate()
	{
		pending = true;
	}

	public boolean changed(IntUnaryOperator resolve)
	{
		if (!pending)
		{
			return false;
		}
		pending = false;
		for (Map.Entry<Integer, Integer> entry : definitions.entrySet())
		{
			if (resolve.applyAsInt(entry.getKey()) != entry.getValue())
			{
				return true;
			}
		}
		return false;
	}

	public void clear()
	{
		definitions.clear();
		pending = false;
	}
}
