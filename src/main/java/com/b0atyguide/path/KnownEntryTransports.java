/*
 * Copyright (c) 2026, Previn <https://github.com/previns>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES ARE DISCLAIMED.
 */
package com.b0atyguide.path;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;

/**
 * Verified one-tile Open and Slash crossings from Shortest Path's transport
 * table.
 *
 * <p>The live client tells us which loaded object is currently an actionable
 * gate or door, but a WallObject's model orientation has repeatedly failed to
 * identify the collision edge it crosses. Shortest Path solves that ambiguity
 * with explicit origin/destination edges. These directions are used only when
 * a live actionable candidate exists on the same tile; the table cannot turn an
 * Inspect wall, absent object, or closed quest barrier into an entrance.
 */
final class KnownEntryTransports
{
	private static final String RESOURCE = "/com/b0atyguide/open-transports.tsv";
	private static final Map<Long, Integer> DIRECTIONS = load();

	private KnownEntryTransports()
	{
	}

	static int directionsAt(WorldPoint point)
	{
		if (point == null)
		{
			return 0;
		}
		return DIRECTIONS.getOrDefault(key(point.getX(), point.getY(), point.getPlane()), 0);
	}

	static int size()
	{
		return DIRECTIONS.size();
	}

	private static Map<Long, Integer> load()
	{
		final InputStream stream = KnownEntryTransports.class.getResourceAsStream(RESOURCE);
		if (stream == null)
		{
			throw new IllegalStateException("missing " + RESOURCE);
		}

		final Map<Long, Integer> directions = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(stream, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.charAt(0) == '#')
				{
					continue;
				}
				final String[] fields = line.split("\\t", -1);
				if (fields.length < 2)
				{
					continue;
				}
				final int[] from = point(fields[0]);
				final int[] to = point(fields[1]);
				if (from == null || to == null || from[2] != to[2])
				{
					continue;
				}
				final int direction = direction(from[0], from[1], to[0], to[1]);
				if (direction != 0)
				{
					directions.merge(key(from[0], from[1], from[2]), direction,
						(left, right) -> left | right);
				}
			}
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("could not read " + RESOURCE, ex);
		}
		return Collections.unmodifiableMap(directions);
	}

	private static int[] point(String text)
	{
		final String[] numbers = text.trim().split("\\s+");
		if (numbers.length != 3)
		{
			return null;
		}
		try
		{
			return new int[]{
				Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]),
				Integer.parseInt(numbers[2]),
			};
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	private static int direction(int x, int y, int toX, int toY)
	{
		if (toX == x && toY == y + 1)
		{
			return CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
		}
		if (toX == x + 1 && toY == y)
		{
			return CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		}
		if (toX == x && toY == y - 1)
		{
			return CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		}
		if (toX == x - 1 && toY == y)
		{
			return CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		return 0;
	}

	private static long key(int x, int y, int plane)
	{
		return ((long) plane << 28) | ((long) x << 14) | y;
	}
}
