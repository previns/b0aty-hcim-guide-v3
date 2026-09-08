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

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Where something is, on the map the guide's coordinates are written in.
 *
 * <p>Inside an instance -- a quest cave, a cutscene, the mine cart ride in The
 * Tourist Trap -- {@code getWorldLocation()} answers in the instance's own
 * coordinates. The plugin was observed drawing a route
 * {@code from=11655,14089}, which is not a place: it is the copy of the mine
 * the player was riding through. Everything downstream had been handed a
 * position eight thousand tiles from the player, and every zone the quest's
 * branches test against had quietly stopped containing them.
 *
 * <p>Quest Helper puts every position through the same conversion, which is why
 * it kept its place through that ride while this did not.
 *
 * <p>Outside an instance it is the identity, so apply it everywhere and stop
 * thinking about it.
 */
public final class RealPoint
{
	private RealPoint()
	{
	}

	/** Where an actor really is, or null when there is no answer. */
	public static WorldPoint of(Client client, Actor actor)
	{
		if (client == null || actor == null)
		{
			return null;
		}
		final LocalPoint at = actor.getLocalLocation();
		return at == null ? actor.getWorldLocation() : WorldPoint.fromLocalInstance(client, at);
	}

	/** The same, for a point the client has already reported. */
	public static WorldPoint of(Client client, WorldPoint said)
	{
		if (client == null || said == null)
		{
			return said;
		}
		final LocalPoint at = LocalPoint.fromWorld(client.getTopLevelWorldView(), said);
		return at == null ? said : WorldPoint.fromLocalInstance(client, at);
	}
}
