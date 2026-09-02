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
package com.b0atyguide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Source-level checks for the things Plugin Hub review looks at and a unit test
 * cannot reach: that everything registered is unregistered, and that nothing
 * left a debug print behind.
 *
 * <p>Reading the source is crude, but the alternative is a running client, and
 * a leak here is invisible until someone toggles the plugin off and on for an
 * hour.
 */
public class LifecycleTest
{
	private static final Path PLUGIN =
		Paths.get("src/main/java/com/b0atyguide/B0atyGuidePlugin.java");

	private static String source(Path path) throws IOException
	{
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static List<String> matches(String text, String regex)
	{
		final List<String> found = new ArrayList<>();
		final Matcher matcher = Pattern.compile(regex).matcher(text);
		while (matcher.find())
		{
			found.add(matcher.group(1));
		}
		return found;
	}

	@Test
	public void everyOverlayAddedIsAlsoRemoved() throws Exception
	{
		final String text = source(PLUGIN);
		final List<String> added = matches(text, "overlayManager\\.add\\((\\w+)\\)");
		final List<String> removed = matches(text, "overlayManager\\.remove\\((\\w+)\\)");
		assertTrue("expected some overlays", added.size() > 1);
		for (String overlay : added)
		{
			assertTrue(overlay + " is added but never removed", removed.contains(overlay));
		}
		assertEquals("an overlay is removed that was never added",
			added.size(), removed.size());
	}

	@Test
	public void everyStatefulTrackerIsClearedOnShutdown() throws Exception
	{
		// These hold NPCs, TileObjects and decoded images. The plugin can be
		// toggled without the client restarting, so anything not cleared is
		// held for the rest of the session.
		final String text = source(PLUGIN);
		final int shutdown = text.indexOf("protected void shutDown");
		assertTrue(shutdown > 0);
		final String body = text.substring(shutdown);

		for (String tracker : new String[]{
			"sceneTracker", "pathTracker", "approachTracker", "bankTracker",
			"withdrawTracker", "worldMapMarker", "bankTags", "sectionImages"})
		{
			assertTrue(tracker + " is not cleared in shutDown()",
				body.contains(tracker + ".clear()") || body.contains(tracker + ".release()"));
		}
	}

	@Test
	public void noSourceFileHasStrayDebugOutput() throws Exception
	{
		final List<String> offenders = new ArrayList<>();
		for (Path path : Files.walk(Paths.get("src/main/java")).filter(
			p -> p.toString().endsWith(".java")).toArray(Path[]::new))
		{
			final String text = source(path);
			if (text.contains("System.out.print") || text.contains("System.err.print")
				|| text.contains("printStackTrace()"))
			{
				offenders.add(path.toString());
			}
		}
		assertTrue("debug output left in " + offenders, offenders.isEmpty());
	}

	@Test
	public void noSourceFileUsesReflection() throws Exception
	{
		// Plugin Hub forbids it, and it is easy to reach for by accident when
		// resolving a constant by name.
		final List<String> offenders = new ArrayList<>();
		for (Path path : Files.walk(Paths.get("src/main/java")).filter(
			p -> p.toString().endsWith(".java")).toArray(Path[]::new))
		{
			final String text = source(path);
			if (text.contains("java.lang.reflect") || text.contains(".getDeclaredMethod(")
				|| text.contains(".getDeclaredField(") || text.contains("Class.forName("))
			{
				offenders.add(path.toString());
			}
		}
		assertTrue("reflection used in " + offenders, offenders.isEmpty());
	}

	/**
	 * Two doc comments in a row mean one of them describes a method that is no
	 * longer beneath it, and javac keeps only the second. This has happened four
	 * times here, always the same way: a blind text replacement matched inside a
	 * method it was not aimed at and carried the comment along. It compiles and
	 * every other test passes, so nothing else catches it.
	 */
	@Test
	public void noDeclarationCarriesTwoDocComments() throws Exception
	{
		final List<String> offenders = new ArrayList<>();
		for (Path path : Files.walk(Paths.get("src/main/java")).filter(
			p -> p.toString().endsWith(".java")).toArray(Path[]::new))
		{
			// Split on the newline only; trim() below takes the carriage return.
			final String[] lines = source(path).split("\n");
			for (int i = 0; i < lines.length - 1; i++)
			{
				if (lines[i].trim().endsWith("*/") && lines[i + 1].trim().startsWith("/*"))
				{
					offenders.add(path.getFileName() + ":" + (i + 1));
				}
			}
		}
		assertTrue("orphaned doc comment above " + offenders, offenders.isEmpty());
	}

	/**
	 * Every bundled resource has to be package-qualified.
	 *
	 * <p>Plugin hub jars are placed on one shared classpath, so a resource named
	 * at the jar root is a name any other plugin can also claim -- and the
	 * winner is whichever jar happens to load first. The failure is silent and
	 * would look like our own data being subtly wrong.
	 */
	@Test
	public void everyBundledResourceIsUnderThePackage() throws Exception
	{
		final Path root = Paths.get("src/main/resources");
		final List<String> offenders = new ArrayList<>();
		for (Path path : Files.walk(root).filter(Files::isRegularFile).toArray(Path[]::new))
		{
			if (!root.relativize(path).startsWith(Paths.get("com/b0atyguide")))
			{
				offenders.add(root.relativize(path).toString());
			}
		}
		assertTrue("resource would sit at the jar root: " + offenders, offenders.isEmpty());
	}

	@Test
	public void everySourceFileCarriesTheCopyrightHeader() throws Exception
	{
		// RuneLite's code conventions: "Add the copyright header to the
		// beginning of every new text file". Easy to forget on the next file
		// added, and a reviewer notices immediately.
		final List<String> missing = new ArrayList<>();
		for (Path path : Files.walk(Paths.get("src")).filter(
			p -> p.toString().endsWith(".java")).toArray(Path[]::new))
		{
			final String text = source(path);
			if (!text.startsWith("/*") || !text.substring(0, Math.min(400, text.length()))
				.contains("Copyright (c)"))
			{
				missing.add(path.toString());
			}
		}
		assertTrue("no copyright header on " + missing, missing.isEmpty());
	}

	@Test
	public void thePluginHubMetadataIsPresent() throws Exception
	{
		// The hub needs all three, and a missing one is found at submission
		// time rather than build time.
		assertTrue("BSD 2-Clause LICENSE is required by the Plugin Hub",
			Files.exists(Paths.get("LICENSE")));
		assertTrue("runelite-plugin.properties describes the plugin to the hub",
			Files.exists(Paths.get("runelite-plugin.properties")));
		assertTrue("icon.png is shown on the hub listing",
			Files.exists(Paths.get("icon.png")));

		final String licence = source(Paths.get("LICENSE"));
		assertTrue("the hub requires BSD 2-Clause", licence.contains("BSD 2-Clause"));
	}

	@Test
	public void theHubIconFitsTheSizeLimit() throws Exception
	{
		// The hub rejects anything larger than 48x72.
		final java.awt.image.BufferedImage icon =
			javax.imageio.ImageIO.read(Paths.get("icon.png").toFile());
		assertTrue("icon.png is unreadable", icon != null);
		assertTrue("icon is " + icon.getWidth() + "px wide, max 48", icon.getWidth() <= 48);
		assertTrue("icon is " + icon.getHeight() + "px tall, max 72", icon.getHeight() <= 72);
	}

	@Test
	public void noSourceFileUsesWildcardImports() throws Exception
	{
		// RuneLite's conventions set the on-demand import threshold to 999.
		final List<String> offenders = new ArrayList<>();
		for (Path path : Files.walk(Paths.get("src")).filter(
			p -> p.toString().endsWith(".java")).toArray(Path[]::new))
		{
			if (Pattern.compile("^import .*\\.\\*;", Pattern.MULTILINE)
				.matcher(source(path)).find())
			{
				offenders.add(path.toString());
			}
		}
		assertTrue("wildcard imports in " + offenders, offenders.isEmpty());
	}
}
