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

import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import com.b0atyguide.overlay.WorldMapMarker;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;

/** Optional integration through Shortest Path's public PluginMessage protocol. */
public final class ShortestPathBridge
{
	private ShortestPathBridge() { }

	public static PluginMessage messageFor(Step step, QuestHelperSteps.Instruction instruction)
	{
		if (step == null) { return null; }
		WorldPoint target = step.getArrivesAt();
		if (target == null)
		{
			if (step.navigationInstruction(instruction) == null && step.getDestination() != null
				&& step.getDestination().isAmbiguous())
			{
				return null;
			}
			final Set<WorldPoint> points = new HashSet<>(WorldMapMarker.locate(step, instruction));
			if (points.size() != 1) { return null; }
			target = points.iterator().next();
		}
		// No config overrides: Shortest Path retains the user's transport and
		// Wilderness preferences. No clear message: other routes remain theirs.
		return new PluginMessage("shortestpath", "path", Collections.singletonMap("target", target));
	}
}
