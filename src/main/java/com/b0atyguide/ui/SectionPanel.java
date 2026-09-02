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

import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.b0atyguide.progress.Progress;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;

/**
 * One bank: a clickable header, and its steps once opened.
 *
 * <p>These are constructed once and reused for the life of the panel.
 * Filtering toggles visibility instead of recreating them, because rebuilding
 * 236 panels per keystroke was the bulk of the search lag.
 *
 * <p>Step rows stay lazy: built the first time a section is expanded, and under
 * a search only the rows that matched are built at all.
 */
public class SectionPanel extends JPanel
{
	// Cached: updateHeader() runs on all 236 sections whenever any step is
	// ticked, and allocating a fresh compound border each time is pure churn.
	private static final Map<Color, Border> HEADER_BORDERS = new HashMap<>();

	private final Section section;
	private final Progress progress;
	private final BiConsumer<Step, Boolean> onToggle;
	private final Consumer<Step> onSelect;
	private final IntSupplier fontSize;
	private final BooleanSupplier collapseCompleted;

	/** Lowercased title plus every step, built once. The search scans this. */
	private final String haystack;

	private final JPanel header = new JPanel(new BorderLayout(6, 0));
	private final JCheckBox bankBox = new JCheckBox();
	private final JLabel titleLabel = new JLabel();
	private final JLabel countLabel = new JLabel();
	private final JPanel stepsPanel = new JPanel();
	private final List<StepRow> rows = new ArrayList<>();

	private String filter = "";
	private boolean expanded;
	private boolean built;

	/** Null in tests, which have no injector and no business fetching images. */
	private final SectionImages images;
	private final BooleanSupplier showImages;
	private JLabel imageLabel;
	private int matchedSteps;

	SectionPanel(
		Section section,
		Progress progress,
		BiConsumer<Step, Boolean> onToggle,
		Consumer<Step> onSelect,
		Consumer<SectionPanel> onHeaderClicked,
		BiConsumer<Section, Boolean> onSectionToggle,
		IntSupplier fontSize,
		BooleanSupplier collapseCompleted,
		SectionImages images,
		BooleanSupplier showImages)
	{
		this.section = section;
		this.progress = progress;
		this.onToggle = onToggle;
		this.onSelect = onSelect;
		this.fontSize = fontSize;
		this.collapseCompleted = collapseCompleted;
		this.images = images;
		this.showImages = showImages;

		final StringBuilder builder = new StringBuilder();
		builder.append(section.getTitle().toLowerCase(Locale.ROOT)).append('\n');
		for (Step step : section.getSteps())
		{
			builder.append(step.getText().toLowerCase(Locale.ROOT)).append('\n');
		}
		haystack = builder.toString();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		header.setCursor(new Cursor(Cursor.HAND_CURSOR));

		titleLabel.setForeground(Color.WHITE);
		countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		applyFonts();

		bankBox.setOpaque(false);
		bankBox.setBorderPainted(false);
		bankBox.setFocusPainted(false);
		bankBox.setMargin(new Insets(0, 0, 0, 0));
		bankBox.setToolTipText(
			"Complete every step in this bank. Un-ticking keeps the steps you "
				+ "ticked yourself.");
		// Consumed here so clicking the box does not also fire the header's
		// jump/expand behaviour.
		bankBox.addActionListener(e -> onSectionToggle.accept(section, bankBox.isSelected()));

		header.add(bankBox, BorderLayout.WEST);
		header.add(titleLabel, BorderLayout.CENTER);
		header.add(countLabel, BorderLayout.EAST);
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				// The panel decides what a header click means: toggle normally,
				// jump out of a search when one is active.
				onHeaderClicked.accept(SectionPanel.this);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				header.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
		stepsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		stepsPanel.setVisible(false);

		add(header, BorderLayout.NORTH);
		add(stepsPanel, BorderLayout.CENTER);

		updateHeader();
	}

	/** Exposed for the layout tests; nothing else should need it. */
	public boolean isExpandedForTest()
	{
		return expanded;
	}

	public Section getSection()
	{
		return section;
	}

	boolean matches(String filter)
	{
		return filter.isEmpty() || haystack.contains(filter);
	}

	/**
	 * Point this section at a new search term.
	 *
	 * <p>Existing rows are discarded rather than filtered in place, because
	 * which rows should exist depends on the term. Discarding is cheap; they are
	 * only rebuilt if the section ends up expanded.
	 */
	void applyFilter(String filter)
	{
		if (this.filter.equals(filter))
		{
			return;
		}
		this.filter = filter;
		discardRows();
		// A search is a request to see the matches, not to go hunting for them.
		setExpanded(!filter.isEmpty() && matches(filter));
	}

	private void discardRows()
	{
		rows.clear();
		stepsPanel.removeAll();
		imageLabel = null;
		built = false;
		matchedSteps = 0;
	}

	void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		if (expanded && !built)
		{
			buildSteps();
		}
		stepsPanel.setVisible(expanded);
		updateHeader();
		revalidate();
		repaint();
	}

	/**
	 * The sidebar changed width, so every wrapped row has to re-derive its
	 * height. Cheap and a no-op for a section whose rows were never built.
	 */
	public void onWidthChanged()
	{
		if (!built)
		{
			return;
		}
		for (StepRow row : rows)
		{
			row.invalidate();
		}
		stepsPanel.revalidate();
		revalidate();
		repaint();
	}

	void toggle()
	{
		setExpanded(!expanded);
	}

	/**
	 * The bank's setup screenshot, above its steps.
	 *
	 * <p>Requested only when the bank is opened, so a player who never expands
	 * one makes no network requests at all. Nothing is shown until it arrives,
	 * and nothing at all if it never does.
	 */
	private void buildImage()
	{
		if (images == null || showImages == null || !showImages.getAsBoolean())
		{
			return;
		}
		final List<String> urls = section.getImageUrls();
		if (urls.isEmpty())
		{
			return;
		}

		imageLabel = new JLabel();
		imageLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		stepsPanel.add(imageLabel);

		final String url = urls.get(0);
		images.get(url, image ->
		{
			if (imageLabel == null)
			{
				return;
			}
			imageLabel.setIcon(new ImageIcon(scaleToWidth(image, imageWidth())));
			revalidate();
			repaint();
		});
	}

	/** The sidebar's width, less the room the panel's own borders take. */
	private int imageWidth()
	{
		final int available = getWidth() - 12;
		return available > 40 ? available : 200;
	}

	/**
	 * Scale to the panel width, never up. Enlarging a screenshot past its own
	 * size just blurs it.
	 */
	private static Image scaleToWidth(BufferedImage image, int width)
	{
		if (image.getWidth() <= width)
		{
			return image;
		}
		final int height = Math.max(1, image.getHeight() * width / image.getWidth());
		return image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
	}

	private void buildSteps()
	{
		built = true;
		buildImage();
		matchedSteps = 0;
		for (Step step : section.getSteps())
		{
			// Under a search, show only the steps that actually matched. "a"
			// otherwise expands every bank into ~2,900 rows nobody asked for,
			// and buries the hits.
			if (!filter.isEmpty()
				&& !step.getText().toLowerCase(Locale.ROOT).contains(filter))
			{
				continue;
			}
			final StepRow row =
				new StepRow(step, progress, onToggle, onSelect, filter, fontSize.getAsInt());
			rows.add(row);
			stepsPanel.add(row);
			matchedSteps++;
		}
	}

	private void updateHeader()
	{
		final int done = progress.completedIn(section);
		final int total = section.getSteps().size();

		titleLabel.setText((expanded ? "- " : "+ ") + section.getDisplayLabel());
		applyFonts();

		if (bankBox.isSelected() != (total > 0 && done == total))
		{
			bankBox.setSelected(total > 0 && done == total);
		}

		// Progress is readable from the header alone: red for untouched, yellow
		// for started, green for finished. The stripe carries it too, so the
		// state survives at a glance while scrolling past collapsed banks.
		final Color status = statusColour(done, total);
		titleLabel.setForeground(status);
		header.setBorder(headerBorder(status));

		if (filter.isEmpty())
		{
			countLabel.setText(done + "/" + total);
			countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else
		{
			// Under a search the header is a jump target, so it says so rather
			// than showing a completion count the search has nothing to do with.
			countLabel.setText(matchedSteps + " hits  >");
			countLabel.setForeground(ColorScheme.BRAND_ORANGE);
		}
	}

	/**
	 * Header type follows the body size so the panel scales as one thing.
	 * Deriving the pixel RuneScape face to an arbitrary size produced uneven
	 * glyph heights, which is what threw the header alignment out.
	 */
	private void applyFonts()
	{
		final int size = fontSize.getAsInt();
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(size, 9)));
		countLabel.setFont(
			new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(size - 2, 9)));
	}

	private static Border headerBorder(Color status)
	{
		return HEADER_BORDERS.computeIfAbsent(status, colour ->
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, colour),
				BorderFactory.createEmptyBorder(6, 6, 6, 8)));
	}

	private static Color statusColour(int done, int total)
	{
		if (total == 0)
		{
			// A section with no steps is neither done nor undone.
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		if (done == 0)
		{
			return ColorScheme.PROGRESS_ERROR_COLOR;
		}
		return done == total
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_INPROGRESS_COLOR;
	}

	void onProgressChanged(Step current)
	{
		updateHeader();

		final boolean holdsCurrent = current != null && containsStep(current);
		if (filter.isEmpty())
		{
			if (holdsCurrent && !expanded)
			{
				setExpanded(true);
			}
			else if (expanded && !holdsCurrent && collapseCompleted.getAsBoolean()
				&& progress.isSectionComplete(section))
			{
				// A finished bank left open pushes the one you are now on off the
				// bottom of the panel.
				setExpanded(false);
			}
		}

		for (StepRow row : rows)
		{
			row.onProgressChanged(current);
		}
	}

	private boolean containsStep(Step target)
	{
		for (Step step : section.getSteps())
		{
			if (step.getId().equals(target.getId()))
			{
				return true;
			}
		}
		return false;
	}
}
