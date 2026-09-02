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

import com.b0atyguide.data.Episode;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.b0atyguide.progress.Progress;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Component;
import java.awt.Point;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import java.awt.event.ComponentAdapter;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentEvent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: a bank jump list, a search box, and every bank as a
 * collapsible section.
 *
 * <p>All 236 sections are built, because a header is three components and
 * hiding the tail behind "176 more banks" made the guide unusable past bank 54.
 * The expensive part is the ~2,900 step rows, and those are built per section on
 * first expand.
 */
@Slf4j
public class GuidePanel extends PluginPanel
{
	/**
	 * Typing rebuilds the whole list, so filtering waits for a pause rather than
	 * running per keystroke. 250ms is below the threshold where the box feels
	 * unresponsive and well above a fast typist's inter-key gap.
	 */
	private static final int SEARCH_DEBOUNCE_MS = 250;

	/** Below this, a search narrows nothing and only costs a rebuild. */
	private static final int MIN_SEARCH_LENGTH = 2;

	private final JPanel listPanel = new ScrollableColumn();
	private final JTextField searchField = new JTextField();
	private final JLabel statusLabel = new JLabel();

	/**
	 * "Watch Episode 4", pointing at the video the current bank appears in.
	 * Hidden until a step is current, since it has nothing to point at.
	 */
	private final JLabel episodeLink = new JLabel();

	/**
	 * "Wiki guide updated 31 Aug 2026". The guide ships with the plugin, so
	 * this is the one honest signal of how current the content is.
	 */
	private final JLabel updatedLabel = new JLabel();
	/** Reads as a link on the panel's dark background, unlike the body grey. */
	private static final Color LINK_BLUE = new Color(110, 175, 255);
	private static final Color LINK_BLUE_HOVER = new Color(165, 205, 255);

	private static final DateTimeFormatter REVISION_FORMAT =
		DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private static final String WIKI_URL =
		"https://oldschool.runescape.wiki/w/Guide:B0aty_HCIM_Guide_V3";

	private final JComboBox<String> jumpBox = new JComboBox<>();
	private final JScrollPane scrollPane;
	private final JLabel emptyLabel = new JLabel("Nothing matches that search");

	private Guide guide;
	private Progress progress;
	private BiConsumer<Step, Boolean> onToggle;
	private Consumer<Step> onSelect;
	private BiConsumer<Section, Boolean> onSectionToggle;
	private IntSupplier fontSize;
	private BooleanSupplier collapseCompleted;
	private Step currentStep;

	/** The video behind {@link #episodeLink}; null when there is none. */
	private String episodeUrl;
	private String currentSectionId;
	private String filter = "";

	private final List<SectionPanel> sectionPanels = new ArrayList<>();

	/** Null in the layout tests, which have no injector. */
	private SectionImages images;
	private BooleanSupplier showImages;
	private BooleanSupplier showFeatures;
	private Consumer<Boolean> onFeaturesDismissed;
	private final Timer searchTimer;
	private boolean suppressJumpEvents;

	public GuidePanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchTimer = new Timer(SEARCH_DEBOUNCE_MS, e -> applyFilter());
		searchTimer.setRepeats(false);

		add(buildHeader(), BorderLayout.NORTH);

		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// The scroll pane's view has to be the Scrollable one, or
		// getScrollableTracksViewportWidth() is never consulted and the viewport
		// sizes the content to its preferred width instead -- which is exactly
		// what pushed rows past the sidebar and clipped them.
		final ScrollableColumn wrapper = new ScrollableColumn();
		wrapper.setLayout(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(listPanel, BorderLayout.NORTH);

		scrollPane = new JScrollPane(
			wrapper,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		// Step text wraps, so every row's height is derived from the width it
		// is given. Rows are built the first time a bank is expanded, and on a
		// fresh client that happens before the sidebar has been laid out --
		// they size themselves against a width of zero and come out jumbled
		// until something forces a second pass, which is why collapsing and
		// re-expanding "fixed" it.
		//
		// Re-laying out the built rows whenever the viewport width changes
		// catches that first real width, and the drag-to-resize case with it.
		scrollPane.getViewport().addComponentListener(new ComponentAdapter()
		{
			private int lastWidth = -1;

			@Override
			public void componentResized(ComponentEvent event)
			{
				final int width = scrollPane.getViewport().getWidth();
				if (width == lastWidth || width <= 0)
				{
					return;
				}
				lastWidth = width;
				for (SectionPanel panel : sectionPanels)
				{
					panel.onWidthChanged();
				}
			}
		});

		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyLabel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * A link out to the guide this plugin renders.
	 *
	 * <p>The panel shows step text and screenshots, not the wiki's own tables,
	 * notes and talk page -- and the guide is edited constantly, so the source
	 * is worth one obvious click rather than a search.
	 */
	private JLabel buildWikiLink()
	{
		final JLabel link = new JLabel("Open the guide on the wiki");
		link.setForeground(LINK_BLUE);
		link.setAlignmentX(LEFT_ALIGNMENT);
		link.setCursor(new Cursor(Cursor.HAND_CURSOR));
		link.setToolTipText(WIKI_URL);
		link.setIconTextGap(6);
		link.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		final BufferedImage icon = wikiIcon();
		if (icon != null)
		{
			link.setIcon(new ImageIcon(icon));
		}

		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				// LinkBrowser asks the player before opening a browser the
				// first time, so this cannot surprise anyone.
				LinkBrowser.browse(WIKI_URL);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(LINK_BLUE_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(LINK_BLUE);
			}
		});
		return link;
	}

	/**
	 * RuneLite's own wiki icon, borrowed from the classpath.
	 *
	 * <p>Better than bundling the wiki's logo ourselves: it is the icon the
	 * client already uses for wiki links, so it looks native and carries no
	 * question about whose artwork it is. It lives in another plugin's
	 * resources though, so a null means it moved and the link simply goes
	 * without -- never a missing-resource failure in front of a player.
	 */
	private static BufferedImage wikiIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(
				GuidePanel.class, "/net/runelite/client/plugins/info/wiki_icon.png");
		}
		catch (RuntimeException e)
		{
			log.debug("no wiki icon on the classpath", e);
			return null;
		}
	}

	/**
	 * Point the episode link at the video covering the current bank.
	 *
	 * <p>The guide is a series of videos as much as a list of steps, and a
	 * player stuck on a bank often wants to watch that part rather than read it
	 * again -- which is why the episodes are worth keeping rather than being
	 * dropdown decoration.
	 */
	private void updateEpisodeLink(Step current)
	{
		final Section section = progress == null ? null : progress.sectionOf(guide, current);
		final Episode episode = guide == null ? null : guide.episodeFor(section);
		final String videoId = episode == null || episode.getVideoIds().isEmpty()
			? null
			: episode.getVideoIds().get(0);

		if (videoId == null)
		{
			episodeUrl = null;
			episodeLink.setVisible(false);
			return;
		}

		final String url = GuideLinks.youTubeUrl(videoId);
		if (!GuideLinks.isAllowed(url))
		{
			episodeUrl = null;
			episodeLink.setVisible(false);
			return;
		}

		episodeUrl = url;
		episodeLink.setText("Watch Episode " + episode.getOrdinal());
		episodeLink.setToolTipText(episode.getTitle());
		episodeLink.setVisible(true);
	}

	/**
	 * Say when the wiki page this was built from was last edited.
	 *
	 * <p>The date of the revision, not of the build. Updates are manual, so a
	 * player deserves to know whether they are reading last week's guide or
	 * last quarter's, and the build date would claim freshness the content
	 * does not have.
	 */
	private void updateRevisionLabel()
	{
		final String iso = guide == null ? null : guide.getSourceRevisionAt();
		if (iso == null || iso.isEmpty())
		{
			updatedLabel.setVisible(false);
			return;
		}
		try
		{
			final LocalDate date = Instant.parse(iso).atZone(ZoneId.systemDefault())
				.toLocalDate();
			updatedLabel.setText("Wiki guide updated " + REVISION_FORMAT.format(date));
			updatedLabel.setToolTipText("Revision " + guide.getSourceRevid()
				+ ", built into this version of the plugin");
			updatedLabel.setVisible(true);
		}
		catch (DateTimeParseException e)
		{
			// A date we cannot read is worse than none: it would be a wrong
			// claim about how current the guide is.
			log.debug("unreadable revision date {}", iso, e);
			updatedLabel.setVisible(false);
		}
	}

	private JPanel buildHeader()
	{
		final JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		configureJumpBox();
		configureSearchField();
		configureLabels();

		header.add(buildWikiLink());
		header.add(Box.createVerticalStrut(4));
		header.add(jumpBox);
		header.add(Box.createVerticalStrut(4));
		header.add(searchField);
		header.add(Box.createVerticalStrut(4));
		header.add(statusLabel);
		header.add(Box.createVerticalStrut(2));
		header.add(episodeLink);
		header.add(Box.createVerticalStrut(2));
		header.add(updatedLabel);
		return header;
	}

	private void configureJumpBox()
	{
		jumpBox.setToolTipText("Jump to a bank");
		jumpBox.setMaximumRowCount(20);
		jumpBox.setAlignmentX(LEFT_ALIGNMENT);
		jumpBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, jumpBox.getPreferredSize().height));
		jumpBox.addActionListener(e ->
		{
			// Rebuilding the model fires this, and acting on it would jump the
			// player somewhere they did not ask to go.
			if (!suppressJumpEvents)
			{
				jumpToSelected();
			}
		});
	}

	private void configureSearchField()
	{
		searchField.setToolTipText("Filter banks and steps");
		searchField.setAlignmentX(LEFT_ALIGNMENT);
		searchField.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, searchField.getPreferredSize().height));

		// Debounced: filtering 2,900 steps on every keystroke is what made the
		// search feel like it was hanging.
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}
		});
	}

	private void configureLabels()
	{
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setAlignmentX(LEFT_ALIGNMENT);

		updatedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		updatedLabel.setAlignmentX(LEFT_ALIGNMENT);

		episodeLink.setForeground(GuideLinks.BLUE);
		episodeLink.setAlignmentX(LEFT_ALIGNMENT);
		episodeLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
		episodeLink.setVisible(false);
		episodeLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (episodeUrl != null)
				{
					LinkBrowser.browse(episodeUrl);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				episodeLink.setForeground(GuideLinks.BLUE_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				episodeLink.setForeground(GuideLinks.BLUE);
			}
		});
	}

	public void init(
		Guide guide,
		Progress progress,
		BiConsumer<Step, Boolean> onToggle,
		Consumer<Step> onSelect,
		BiConsumer<Section, Boolean> onSectionToggle,
		IntSupplier fontSize,
		BooleanSupplier collapseCompleted,
		SectionImages images,
		BooleanSupplier showImages,
		BooleanSupplier showFeatures,
		Consumer<Boolean> onFeaturesDismissed)
	{
		this.guide = guide;
		this.progress = progress;
		this.images = images;
		this.showImages = showImages;
		this.showFeatures = showFeatures;
		this.onFeaturesDismissed = onFeaturesDismissed;
		updateRevisionLabel();
		this.onToggle = onToggle;
		this.onSelect = onSelect;
		this.onSectionToggle = onSectionToggle;
		this.fontSize = fontSize;
		this.collapseCompleted = collapseCompleted;

		applyHeaderFonts();
		buildSections();
		buildJumpList();
	}

	/** Keep the controls at the top in step with the configured body size. */
	private void applyHeaderFonts()
	{
		final int size = Math.max(fontSize.getAsInt(), 9);
		final Font control = new Font(Font.SANS_SERIF, Font.PLAIN, size);
		searchField.setFont(control);
		jumpBox.setFont(control);
		statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(size - 2, 9)));

		// The controls are fixed-height, so their maximum has to be recomputed
		// whenever the font changes or BoxLayout stretches them.
		searchField.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, searchField.getPreferredSize().height));
		jumpBox.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, jumpBox.getPreferredSize().height));
	}

	/**
	 * Build all 236 sections once and add them all. Filtering then only toggles
	 * visibility, so typing never allocates a Swing component.
	 */
	private void buildSections()
	{
		listPanel.removeAll();
		sectionPanels.clear();

		// Above the banks so it scrolls away, rather than in the header where
		// it would cost height for the rest of the run.
		if (showFeatures != null && showFeatures.getAsBoolean())
		{
			listPanel.add(new FeaturesPanel(fontSize, onFeaturesDismissed, true));
		}

		for (Section section : guide.getSections())
		{
			final SectionPanel panel = new SectionPanel(
				section, progress, onToggle, onSelect, this::onHeaderClicked,
				onSectionToggle, fontSize, collapseCompleted, images, showImages);
			sectionPanels.add(panel);
			listPanel.add(panel);
		}

		emptyLabel.setVisible(false);
		listPanel.add(emptyLabel);
		updateStatus();
	}

	/**
	 * Expand the first {@code count} banks. Exists so the layout tests can get
	 * real step rows in front of a layout pass; nothing expands on its own.
	 */
	public void expandForTest(int count)
	{
		for (int i = 0; i < Math.min(count, sectionPanels.size()); i++)
		{
			sectionPanels.get(i).setExpanded(true);
		}
	}

	/** Exposed for the layout tests; nothing else should need it. */
	public SectionPanel sectionPanelForTest(String sectionId)
	{
		for (SectionPanel panel : sectionPanels)
		{
			if (panel.getSection().getId().equals(sectionId))
			{
				return panel;
			}
		}
		return null;
	}

	/**
	 * A header click means "open this bank" normally, but during a search it
	 * means "take me there": the search is cleared so the bank appears in its
	 * real context, which a filtered list cannot show.
	 */
	private void onHeaderClicked(SectionPanel panel)
	{
		if (filter.isEmpty())
		{
			panel.toggle();
			return;
		}
		clearSearch();
		panel.setExpanded(true);
		scrollTo(panel);
	}

	/**
	 * Releases what the panel holds when the plugin is turned off.
	 *
	 * <p>The panel outlives {@code shutDown()} until Swing drops it, and a
	 * pending search would otherwise fire its debounce against a sidebar the
	 * player can no longer see. The guide and the callbacks go with it: they
	 * reach back into the plugin, and the plugin can be re-enabled without the
	 * client restarting.
	 */
	public void deinit()
	{
		searchTimer.stop();
		sectionPanels.clear();
		listPanel.removeAll();
		guide = null;
		progress = null;
		currentStep = null;
		images = null;
		onToggle = null;
		onSelect = null;
		onSectionToggle = null;
		onFeaturesDismissed = null;
	}

	private void clearSearch()
	{
		searchTimer.stop();
		searchField.setText("");
		filter = "";
		refilter();
	}

	private void buildJumpList()
	{
		final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		model.addElement("Jump to bank...");
		Integer episode = null;
		for (Section section : guide.getSections())
		{
			if (section.getEpisodeOrdinal() != null
				&& !section.getEpisodeOrdinal().equals(episode))
			{
				episode = section.getEpisodeOrdinal();
				model.addElement("-- Episode " + episode + " --");
			}
			model.addElement("   " + section.getDisplayLabel());
		}
		suppressJumpEvents = true;
		jumpBox.setModel(model);
		jumpBox.setSelectedIndex(0);
		suppressJumpEvents = false;
	}

	private void jumpToSelected()
	{
		final Object selected = jumpBox.getSelectedItem();
		if (!(selected instanceof String))
		{
			return;
		}
		final String label = ((String) selected).trim();
		if (label.startsWith("--") || label.startsWith("Jump to"))
		{
			return;
		}

		for (SectionPanel panel : sectionPanels)
		{
			if (panel.getSection().getDisplayLabel().equals(label))
			{
				if (!filter.isEmpty())
				{
					clearSearch();
				}
				panel.setExpanded(true);
				scrollTo(panel);
				return;
			}
		}
	}

	/**
	 * Put a bank's header at the very top of the viewport.
	 *
	 * <p>Not scrollRectToVisible: that only guarantees the rectangle is *on
	 * screen*, so a section already partly visible does not move, and one near
	 * the end of the list stops wherever the content runs out. Setting the view
	 * position says exactly where to land.
	 */
	private void scrollTo(SectionPanel panel)
	{
		SwingUtilities.invokeLater(() ->
		{
			// Expanding a section changes the geometry this depends on, and
			// revalidate() only schedules that work. Force it first, or the
			// offset is computed against the previous layout.
			scrollPane.validate();

			final JViewport viewport = scrollPane.getViewport();
			final Component view = viewport.getView();
			if (view == null || !panel.isShowing() && panel.getParent() == null)
			{
				return;
			}

			final Point top = SwingUtilities.convertPoint(panel, 0, 0, view);
			final int furthest = Math.max(0, view.getHeight() - viewport.getHeight());
			viewport.setViewPosition(
				new Point(0, Math.max(0, Math.min(top.y, furthest))));
		});
	}

	public void refresh(Step current)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.currentStep = current;
			updateEpisodeLink(current);
			updateStatus();
			for (SectionPanel section : sectionPanels)
			{
				section.onProgressChanged(current);
			}

			followCurrentSection(current);
		});
	}

	/**
	 * When finishing a bank moves the current step into the next one, bring that
	 * bank to the top.
	 *
	 * <p>Only on a change of bank. Doing it on every tick would drag the list out
	 * from under someone working through a bank by hand, and the completed bank
	 * collapsing shifts everything below it anyway -- which is what made the
	 * scroll position look random.
	 */
	private void followCurrentSection(Step current)
	{
		if (!filter.isEmpty())
		{
			// During a search the list is a result set, not the route.
			return;
		}

		final Section section = progress.sectionOf(guide, current);
		final String sectionId = section == null ? null : section.getId();
		if (sectionId == null || sectionId.equals(currentSectionId))
		{
			return;
		}
		currentSectionId = sectionId;

		for (SectionPanel panel : sectionPanels)
		{
			if (panel.getSection().getId().equals(sectionId))
			{
				scrollTo(panel);
				return;
			}
		}
	}

	private void applyFilter()
	{
		String next = searchField.getText().trim().toLowerCase(Locale.ROOT);
		// A single character matches almost every bank, so filtering on it costs
		// a full rebuild to show you the whole guide again. Treat it as no
		// filter until there is enough to narrow anything.
		if (next.length() < MIN_SEARCH_LENGTH)
		{
			next = "";
		}
		if (next.equals(filter))
		{
			return;
		}
		filter = next;
		refilter();
	}

	private void updateStatus()
	{
		if (guide == null)
		{
			return;
		}
		final int done = progress.count();
		final int total = guide.getStepCount();
		final int percent = total == 0 ? 0 : (int) Math.round(100.0 * done / total);

		final StringBuilder text = new StringBuilder("<html>");
		text.append(done).append(" / ").append(total).append(" steps (").append(percent).append("%)");

		final Section section = progress.sectionOf(guide, currentStep);
		if (section != null)
		{
			text.append("<br>now: ").append(escape(section.getDisplayLabel()));
		}
		text.append("</html>");
		statusLabel.setText(text.toString());
	}

	private static String escape(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Show the sections that match and hide the rest.
	 *
	 * <p>No components are created or destroyed here -- that is the difference
	 * between this and the previous version, which rebuilt the whole list on
	 * every keystroke.
	 */
	private void refilter()
	{
		if (guide == null)
		{
			return;
		}

		int shown = 0;
		for (SectionPanel panel : sectionPanels)
		{
			final boolean matched = panel.matches(filter);
			if (matched)
			{
				panel.applyFilter(filter);
				shown++;
			}
			panel.setVisible(matched);
		}

		emptyLabel.setVisible(shown == 0);
		updateStatus();
		listPanel.revalidate();
		listPanel.repaint();
		scrollPane.getVerticalScrollBar().setValue(0);
	}
}
