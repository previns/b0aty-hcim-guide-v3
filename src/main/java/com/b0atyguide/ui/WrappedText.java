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
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

/**
 * Read-only text that genuinely reflows to whatever width it is given.
 *
 * <p>This replaces an HTML {@code JLabel}. An HTML label does not reflow to its
 * container -- the wrap width has to be baked into the markup -- and every way
 * of arriving at that number was wrong in a different way: a budget of guessed
 * component widths was simply inaccurate, measuring the container missed
 * Swing's default {@code <body>} margin, and neither survives a single
 * unbreakable token such as a URL, which forces the label wider than the
 * viewport and clips the whole row.
 *
 * <p>A wrapping {@code JTextArea} has none of those problems: it wraps to its
 * allocated width, breaks over-long words instead of overflowing, and supports
 * search highlighting through the standard {@link Highlighter} API rather than
 * by splicing markup around match offsets.
 */
class WrappedText extends JTextArea
{
	/**
	 * Translucent, so the white text stays readable on top of it. A solid
	 * highlight would need the foreground swapped too.
	 */
	private static final Highlighter.HighlightPainter MATCH_PAINTER =
		new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 152, 31, 110));

	WrappedText(String text, Color foreground)
	{
		super(text);
		setLineWrap(true);
		setWrapStyleWord(true);
		setEditable(false);
		setOpaque(false);
		setFocusable(false);
		setBorder(null);
		// A JTextArea carries its own margin on top of its border, so clearing
		// the border alone still leaves the text inset from where it should sit.
		setMargin(new Insets(0, 0, 0, 0));
		setForeground(foreground);
		// A JTextArea reports a preferred width wide enough for its longest line
		// until something constrains it. Zero here lets the parent's width win,
		// and line wrapping then derives the height.
		setMinimumSize(new Dimension(0, 0));
	}

	void highlightMatches(String filter)
	{
		final Highlighter highlighter = getHighlighter();
		highlighter.removeAllHighlights();
		if (filter == null || filter.isEmpty())
		{
			return;
		}

		final String haystack = getText().toLowerCase(Locale.ROOT);
		int at = haystack.indexOf(filter);
		while (at >= 0)
		{
			try
			{
				highlighter.addHighlight(at, at + filter.length(), MATCH_PAINTER);
			}
			catch (BadLocationException e)
			{
				// Offsets come from this component's own text, so this cannot
				// happen; if it somehow does, a missing highlight is not worth
				// failing a render for.
				return;
			}
			at = haystack.indexOf(filter, at + filter.length());
		}
	}
}
