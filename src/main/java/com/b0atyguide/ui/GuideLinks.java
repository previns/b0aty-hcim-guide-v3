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
package com.b0atyguide.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import net.runelite.client.util.LinkBrowser;
import okhttp3.HttpUrl;

/**
 * Links out of the panel, and the rule for which ones are allowed.
 *
 * <p>Every URL here comes from a wiki page anyone can edit, so the host is
 * checked rather than trusted -- the same reasoning as the bank screenshots.
 * An unchecked link is worse than an unchecked image: a picture is only shown,
 * a link opens the player's browser wherever it points.
 */
public final class GuideLinks
{
	/** Reads as a link on the panel's dark background, unlike the body grey. */
	static final Color BLUE = new Color(110, 175, 255);
	static final Color BLUE_HOVER = new Color(165, 205, 255);

	/**
	 * Hosts the guide legitimately links to: the video the episode is, and the
	 * wiki it comes from. Everything else is shown as plain text.
	 */
	private static final List<String> ALLOWED_HOSTS = Arrays.asList(
		"youtube.com", "www.youtube.com", "youtu.be",
		"oldschool.runescape.wiki");

	private GuideLinks()
	{
	}

	public static String youTubeUrl(String videoId)
	{
		return "https://www.youtube.com/watch?v=" + videoId;
	}

	/**
	 * Whether the panel is willing to open this address.
	 *
	 * <p>Matched on the exact host, never a suffix: "youtube.com.evil.example"
	 * ends with the allowed name and is not YouTube.
	 */
	public static boolean isAllowed(String url)
	{
		final HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
		if (parsed == null || !"https".equals(parsed.scheme()))
		{
			return false;
		}
		final String host = parsed.host().toLowerCase(Locale.ROOT);
		return ALLOWED_HOSTS.contains(host);
	}

	/**
	 * A blue, clickable label. Returns null when the URL is not one the panel
	 * will open, so callers render nothing rather than a dead link.
	 */
	public static JLabel link(String text, String url, String tooltip)
	{
		if (!isAllowed(url))
		{
			return null;
		}

		final JLabel label = new JLabel(text);
		label.setForeground(BLUE);
		label.setCursor(new Cursor(Cursor.HAND_CURSOR));
		label.setToolTipText(tooltip == null ? url : tooltip);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				// LinkBrowser asks the player before opening a browser the
				// first time, so this cannot surprise anyone.
				LinkBrowser.browse(url);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setForeground(BLUE_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setForeground(BLUE);
			}
		});
		return label;
	}
}
