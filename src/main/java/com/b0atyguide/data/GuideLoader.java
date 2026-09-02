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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads guide.json and refuses anything it cannot render.
 *
 * <p>The plugin is a renderer over this file, so a malformed one has to fail
 * loudly here rather than producing an empty panel or an exception on the
 * client thread three seconds later.
 */
public class GuideLoader
{
	public static final int SUPPORTED_SCHEMA_VERSION = 1;
	// Package-qualified, not "/guide.json". Plugin hub jars share one
	// classpath, so a resource at the jar root is a name another plugin
	// can already have claimed -- and whichever jar loads first wins.
	private static final String BUNDLED_PATH = "/com/b0atyguide/guide.json";

	private final Gson gson;

	public GuideLoader(Gson gson)
	{
		this.gson = gson;
	}

	/**
	 * Thrown when guide.json is absent, unparseable, or structurally unusable.
	 * Never swallowed: a plugin showing a silently empty guide is worse than
	 * one that says why it is empty.
	 */
	public static class GuideLoadException extends Exception
	{
		public GuideLoadException(String message)
		{
			super(message);
		}

		public GuideLoadException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}

	public Guide loadBundled() throws GuideLoadException
	{
		// getResourceAsStream, not a file URL: once deployed to the plugin hub
		// this resource lives inside a jar.
		try (InputStream stream = GuideLoader.class.getResourceAsStream(BUNDLED_PATH))
		{
			if (stream == null)
			{
				throw new GuideLoadException("guide.json is missing from the plugin resources");
			}
			return load(stream);
		}
		catch (IOException e)
		{
			throw new GuideLoadException("could not read the bundled guide.json", e);
		}
	}

	public Guide load(InputStream stream) throws GuideLoadException
	{
		final Guide guide;
		try
		{
			guide = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), Guide.class);
		}
		catch (JsonSyntaxException e)
		{
			throw new GuideLoadException("guide.json is not valid JSON", e);
		}

		if (guide == null)
		{
			throw new GuideLoadException("guide.json was empty");
		}
		validate(guide);
		return guide;
	}

	/**
	 * Structural checks the renderer depends on. Deliberately cheap -- the
	 * pipeline's validate.py is the thorough one; this guards against a
	 * truncated download or a future schema the plugin predates.
	 */
	private void validate(Guide guide) throws GuideLoadException
	{
		if (guide.getSchemaVersion() != SUPPORTED_SCHEMA_VERSION)
		{
			throw new GuideLoadException(
				"guide.json is schema version " + guide.getSchemaVersion()
					+ " but this plugin understands version " + SUPPORTED_SCHEMA_VERSION
					+ ". Update the plugin.");
		}

		if (guide.getSections().isEmpty())
		{
			throw new GuideLoadException("guide.json contains no sections");
		}

		final Set<String> stepIds = new HashSet<>();
		for (Section section : guide.getSections())
		{
			if (section.getId() == null || section.getSlug() == null)
			{
				throw new GuideLoadException("a section is missing its id or slug");
			}
			for (Step step : section.getSteps())
			{
				if (step.getId() == null)
				{
					throw new GuideLoadException(
						"a step in " + section.getSlug() + " is missing its id");
				}
				if (!stepIds.add(step.getId()))
				{
					// Progress is keyed by step id, so a collision would tick
					// two unrelated steps together.
					throw new GuideLoadException("duplicate step id " + step.getId());
				}
			}
		}
	}
}
