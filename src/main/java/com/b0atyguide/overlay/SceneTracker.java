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
package com.b0atyguide.overlay;

import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import com.b0atyguide.path.RealPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;

/**
 * Finds the current step's target among the things actually loaded around the
 * player.
 *
 * <p>Matching is by <em>name</em>, and by ID only when the wiki confirmed one.
 * That ordering is the whole safety argument for the plugin: the guide's
 * wording is turned into a name by a grammar that is sometimes wrong, and a
 * wrong name matches nothing in the scene. A bad guess costs a missing
 * highlight, never a highlight on the wrong thing.
 */
@Singleton
public class SceneTracker
{
	/**
	 * How near a match has to be for the coordinate to be believed.
	 *
	 * <p>Generous, because it only decides whether to narrow at all. Quest
	 * Helper's coordinates are exact and the wiki's are within a building.
	 */
	private static final int NEAR = 25;

	/**
	 * How far from its coordinate a step's other matches still count.
	 *
	 * <p>Quest Helper's {@code maxRoamRange}, which is what it allows an npc to
	 * have wandered.
	 */
	private static final int ROAM = 48;

	/**
	 * How far {@code showAllInArea} reaches for scenery.
	 *
	 * <p>Quest Helper's {@code ObjectStep.maxObjectDistance}. Wider than the npc
	 * range because it is measured from one coordinate over a room full of the
	 * same thing -- every crate in the Lumberyard -- rather than from a person
	 * who has walked off.
	 */
	private static final int FAR = 50;

	@Inject
	private Client client;

	@Inject
	private GroundItemTracker groundItems;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SearchedObjects searched;

	private final List<NPC> npcs = new ArrayList<>();
	private final List<TileObject> objects = new ArrayList<>();

	private Step step;
	private String sectionLabel;
	private List<String> wantedNames = Collections.emptyList();
	private Set<Integer> wantedIds = Collections.emptySet();
	/** What Quest Helper is waiting on, or null off-quest. */
	private QuestHelperSteps.Instruction instruction;

	private boolean wantNpc;
	private boolean wantObject;

	/**
	 * Where the step says the thing is, when it says.
	 *
	 * <p>Several coordinates for a name the wiki maps in more than one place;
	 * exactly one when Quest Helper named it.
	 */
	private List<WorldPoint> wantedAt = Collections.emptyList();

	/** Whether every match near the coordinate counts, not only the nearest. */
	private boolean spread;

	/**
	 * Which source won, for the diagnostic overlay to show.
	 *
	 * <p>Three can supply what to look for and the choice between them is the
	 * thing that goes wrong, so it is worth being able to read it back.
	 */
	private String source = "nothing";

	/**
	 * Whether {@link #wantedAt} is exact rather than approximate.
	 *
	 * <p>Quest Helper names the tile; the wiki drops a marker somewhere on the
	 * subject of a page. Only the first can be held to.
	 */
	private boolean exactly;

	/**
	 * Outline whatever stands on the wanted tile, because Quest Helper named a
	 * tile and not an id.
	 *
	 * <p>2,720 of its object steps reach us with a coordinate and no id: the
	 * constant they name does not exist in the released runelite-api, so it
	 * resolves to nothing. Quest Helper still outlines them, because it was
	 * compiled against a client that has the id and we were not. The tile is
	 * the same tile, and the object standing on it is the object it means.
	 */
	private boolean onTile;
	private Step.Travel activeTravel;
	private boolean travelArrived;
	private WorldPoint lastTravelAt;

	/**
	 * Point the tracker at a step. Cheap enough to call on every panel
	 * selection; the scene rescan is deferred to the client thread.
	 */
	public void setStep(Step step, String sectionLabel)
	{
		setStep(step, sectionLabel, null);
	}

	/**
	 * Point the tracker at a step, and at what Quest Helper says to do right
	 * now.
	 *
	 * <p>The guide advances a quest a few steps at a time, so on a quest step
	 * its own words ("continue Rune Mysteries") name nothing to outline while
	 * Quest Helper knows exactly which NPC or object is next. That instruction
	 * is matched alongside the step's own target rather than instead of it:
	 * the guide's target is what the player asked for, and the instruction is
	 * what the game is waiting on.
	 *
	 * @param instruction the current Quest Helper instruction, or null
	 */
	public void setStep(Step step, String sectionLabel,
		QuestHelperSteps.Instruction instruction)
	{
		// All of it on the client thread, because that is where all of it is
		// read. Ticking a step off comes from the panel, on the event thread,
		// and every field below is then read by the overlays as they render --
		// none of them volatile, so the render thread was free to go on seeing
		// the step before the one the player had just completed. It did: the
		// sidebar moved on to the next step while the overlay stayed on the
		// last, pointing at nothing, until something else happened to publish
		// the write.
		//
		// Deferring is right from either side: a caller already on the client
		// thread runs on the next tick instead of inline, which is invisible.
		onClientThread(() -> apply(step, sectionLabel, instruction));
	}

	/**
	 * Run on the client thread, or here and now if there is not one.
	 *
	 * <p>The second case is a unit test. Everything below this line is pure --
	 * it decides what to look for from the step and the instruction -- so a test
	 * can drive it without a client, and that is worth more than insisting on a
	 * thread that does not exist.
	 */
	private void onClientThread(Runnable work)
	{
		if (clientThread == null)
		{
			work.run();
			return;
		}
		clientThread.invokeLater(work);
	}

	private void apply(Step step, String sectionLabel,
		QuestHelperSteps.Instruction instruction)
	{
		if (this.step != step)
		{
			travelArrived = false;
			lastTravelAt = null;
		}
		this.step = step;
		this.sectionLabel = sectionLabel;
		this.instruction = instruction;
		activeTravel = step == null || travelArrived ? null : step.getTravel();
		if (activeTravel != null && client != null
			&& activeTravel.atArrival(RealPoint.of(client, client.getLocalPlayer())))
		{
			travelArrived = true;
			activeTravel = null;
		}
		instruction = getNavigationInstruction();

		final Target target = step == null ? null : step.getTarget();
		final boolean usable = target != null && target.isHighlightable();
		// An instruction with no id still speaks if it names a tile and says the
		// thing is scenery. Requiring ids here is what left Death on the Isle's
		// window, cellar and wine unmarked while Quest Helper outlined them.
		final boolean hasInstruction = instruction != null
			&& (!instruction.getIds().isEmpty()
				|| (instruction.isObject() && !instruction.getPoint().isEmpty()));
		final List<Step.Seller> sellers = step == null
			? Collections.emptyList() : step.getSellers();
		final Step.Travel travel = activeTravel;

		if (!usable && !hasInstruction && sellers.isEmpty() && travel == null)
		{
			// Keep the step. Most steps have nothing to outline, and the current
			// step still has to be readable on the step overlay -- forgetting it
			// here is what made the overlay blank on exactly the steps that need
			// it most.
			clearMatches();
			return;
		}

		// Which of the two is speaking about a thing, and which about a region.
		//
		// The guide wins when it names something with ids of its own: that is
		// the step. "Trade Heckel Funch or Hudo and buy Dwellberries" is one
		// errand inside Plague City, and answering it with the quest's current
		// instruction -- Edmond, at the other end of Ardougne, from the start of
		// a quest the player is already halfway through -- is not an
		// improvement, it is a different step.
		//
		// Quest Helper wins when the guide names a place or nothing. "Return to
		// Ardougne & complete Plague City" resolves to Ardougne the town; Quest
		// Helper knows which door. 326 of the 686 quest steps are that shape.
		// A bounded quest instruction names an outcome, not one permanent NPC.
		// Its live quest step must move on to the raft/rope/bookcase as required.
		final boolean milestone = step != null && step.isQuestStep() && step.isQuestFollow();
		final boolean specific = usable && !target.getIds().isEmpty() && !milestone;
		if (hasInstruction && !specific)
		{
			source = "quest helper";
			wantedNames = Collections.emptyList();
			wantedIds = new HashSet<>(instruction.getIds());
			wantNpc = instruction.isNpc();
			wantObject = instruction.isObject();
			spread = instruction.isSpread();

			final WorldPoint here = pointOf(instruction.getPoint());
			wantedAt = here == null
				? Collections.emptyList() : Collections.singletonList(here);
			// Its coordinate is the tile, so nothing near it means none of these
			// is the one. A wiki coordinate is a marker dropped somewhere on the
			// subject of a page and cannot be held to that.
			exactly = here != null;
			// No id to match, so the tile is the whole of the match. Strictly
			// that tile: this is not a search radius, it is Quest Helper saying
			// the thing is there.
			onTile = wantedIds.isEmpty() && here != null;
		}
		else
		{
			source = usable ? "the guide's own target" : "nothing";
			onTile = false;
			wantedNames = usable ? target.getNames() : Collections.emptyList();
			wantedIds = new HashSet<>(usable ? target.getIds() : Collections.emptyList());
			wantNpc = usable && Target.KIND_NPC.equals(target.getKind());
			wantObject = usable && Target.KIND_OBJECT.equals(target.getKind());
			wantedAt = usable ? points(target.getPoints()) : Collections.emptyList();
			// A name the wiki maps in several places is several places, not one.
			spread = usable && target.getPoints().size() > 1;
			exactly = false;

			// "Take the boat to Rimmington" names a place and nothing else. The
			// transport table knows who sails there, so outline them.
			//
			// Asked of npcs *and* objects, because the id spaces overlap and the
			// build refuses to guess which one a transport id is -- 7789 is
			// Holgart and also a calquat tree. Only one of them is at the dock.
			if (travel != null)
			{
				source = "the way there";
				activeTravel = travel;
				wantedIds = new HashSet<>(travel.getIds());
				wantNpc = true;
				wantObject = true;
				// Several docks sail to the same island; the nearest is the one
				// the player is standing at.
				wantedAt = points(travel.getOrigins());
				spread = false;
				// All departures are candidates, not simultaneous destinations.
				// Limit the scene search to the nearest boarding stop so scene
				// iteration order cannot pick a more distant cart or captain.
				if (client != null)
				{
					final WorldPoint nearest = travel.nearestOrigin(RealPoint.of(client, client.getLocalPlayer()));
					wantedAt = nearest == null ? Collections.emptyList() : Collections.singletonList(nearest);
				}
			}
		}

		// Whoever sells what the step says to buy. Every shop that stocks it,
		// because which one is right depends on where the player is standing --
		// and that is settled below, by keeping the matches nearest to them.
		if (!sellers.isEmpty() && !usable && !hasInstruction && travel == null)
		{
			source = "shopkeepers who sell it";
			for (Step.Seller seller : sellers)
			{
				wantedIds.addAll(seller.getIds());
			}
			wantNpc = true;
			// Not narrowed to a coordinate: the shops are all over the world and
			// the one that matters is the one in the room.
			wantedAt = Collections.emptyList();
			spread = false;
		}

		npcs.clear();
		objects.clear();
		if (client != null)
		{
			rescan();
		}
	}

	/**
	 * The Quest Helper instruction in force, or null.
	 *
	 * <p>Carries a coordinate even where its constant could not be resolved to
	 * ids, so a step with nothing to outline can still be routed to.
	 */
	public QuestHelperSteps.Instruction getInstruction()
	{
		return instruction;
	}

	/** The navigation subset; the full instruction remains available to dialogue. */
	public QuestHelperSteps.Instruction getNavigationInstruction()
	{
		return step == null || activeTravel != null ? null : step.navigationInstruction(instruction);
	}

	public Step.Travel getActiveTravel()
	{
		return activeTravel;
	}

	/** Navigation phase only: arriving never checks off a compound guide step.
	 * Keep the arrival latched through quest-instruction refreshes; otherwise
	 * walking onward from the stop sends the player back to board again.
	 */
	public void updateTravel(WorldPoint at)
	{
		if (activeTravel == null || at == null || at.equals(lastTravelAt)) { return; }
		lastTravelAt = at;
		if (activeTravel.atArrival(at)) { travelArrived = true; }
		apply(step, sectionLabel, instruction);
	}

	public void clear()
	{
		onClientThread(() ->
		{
			step = null;
			sectionLabel = null;
			instruction = null;
			activeTravel = null;
			travelArrived = false;
			lastTravelAt = null;
			clearMatches();
		});
	}

	/** Forget what we were looking for, but not which step we are on. */
	private void clearMatches()
	{
		wantedNames = Collections.emptyList();
		wantedIds = Collections.emptySet();
		wantedAt = Collections.emptyList();
		spread = false;
		exactly = false;
		onTile = false;
		source = "nothing";
		wantNpc = false;
		wantObject = false;
		npcs.clear();
		objects.clear();
	}

	/**
	 * Whether there is anything in the scene worth looking for.
	 *
	 * <p>This asked only whether the step <em>named</em> something, and it gates
	 * both the model outline and the minimap arrow. On a quest step the guide
	 * names the quest -- "continue Gertrude's Cat" -- and everything to look for
	 * comes from Quest Helper's instruction as ids, with no name at all, so both
	 * were switched off on exactly the steps that had the best data.
	 */
	public boolean isTracking()
	{
		// Or a tile, when that is all Quest Helper gave: the outline and the
		// minimap arrow are exactly what those steps were missing.
		return !wantedNames.isEmpty() || !wantedIds.isEmpty() || onTile;
	}

	public Step getStep()
	{
		return step;
	}

	/** "Bank 12", for the step overlay's heading. Null before a step is set. */
	public String getSectionLabel()
	{
		return sectionLabel;
	}

	public List<NPC> getNpcs()
	{
		return npcs;
	}

	/**
	 * The matches worth outlining, minus the ones already tried.
	 *
	 * <p>Only ever narrows while several are on screen -- one match is never
	 * dimmed, so a single dig spot or a single gate is untouched by this. And
	 * if every match has been tried, the memory is wrong rather than the player:
	 * it is cleared and all of them come back.
	 *
	 * <p>Returns the list itself when nothing has been tried, which is almost
	 * always, so the common path allocates nothing on a render pass.
	 */
	public List<TileObject> getObjects()
	{
		// Null when a test constructs this directly, as several do -- the same
		// allowance the client thread gets above.
		if (searched == null || searched.isEmpty() || objects.size() < 2)
		{
			return objects;
		}

		final List<TileObject> left = new ArrayList<>(objects.size());
		for (TileObject object : objects)
		{
			if (!searched.isSearched(object.getWorldLocation()))
			{
				left.add(object);
			}
		}

		if (left.isEmpty())
		{
			searched.clear();
			return objects;
		}
		return left;
	}

	// --- what it decided, for the diagnostic ---------------------------------

	/** Which source the current search came from, in plain words. */
	public String getSource()
	{
		return source;
	}

	/** The ids being looked for right now. */
	public Set<Integer> getWantedIds()
	{
		return Collections.unmodifiableSet(wantedIds);
	}

	/** The names being matched at runtime, where no id was resolved. */
	public List<String> getWantedNames()
	{
		return wantedNames;
	}

	/** Where the step says the thing is, if it says. */
	public List<WorldPoint> getWantedAt()
	{
		return Collections.unmodifiableList(wantedAt);
	}

	// --- matching ----------------------------------------------------------

	private boolean nameMatches(String candidate)
	{
		if (candidate == null)
		{
			return false;
		}
		// The wiki's page title and the game's name differ in case and in
		// non-breaking spaces often enough to matter.
		final String normalised = normalise(candidate);
		for (String wanted : wantedNames)
		{
			if (wanted != null && normalise(wanted).equals(normalised))
			{
				return true;
			}
		}
		return false;
	}

	private static String normalise(String value)
	{
		return value.replace(' ', ' ').trim().toLowerCase();
	}

	private boolean npcMatches(NPC npc)
	{
		if (!wantNpc)
		{
			return false;
		}
		if (activeTravel != null)
		{
			if (distance(realPointOf(npc)) >= ROAM) { return false; }
			NPCComposition composition = npc.getTransformedComposition();
			return activeTravel.matchesDeparture(npc.getId(), realPointOf(npc), ROAM - 1)
				|| (composition != null && activeTravel.matchesDeparture(composition.getId(), realPointOf(npc), ROAM - 1));
		}
		if (!wantedIds.isEmpty() && wantedIds.contains(npc.getId()))
		{
			return true;
		}
		// The id an npc reports is the one it currently wears. A great many of
		// them transform -- by quest progress, by time of day, by having been
		// spoken to -- and the id the build resolved is only one of those, so
		// asking the composition is the difference between finding the npc and
		// finding nothing. Quest Helper carries an npcName alongside the id for
		// the same reason, which is the second half of this.
		final NPCComposition composition = npc.getTransformedComposition();
		if (composition != null && !wantedIds.isEmpty()
			&& wantedIds.contains(composition.getId()))
		{
			return true;
		}
		return nameMatches(npc.getName())
			|| (composition != null && nameMatches(composition.getName()));
	}

	private boolean objectMatches(TileObject object)
	{
		if (!wantObject)
		{
			return false;
		}
		if (activeTravel != null)
		{
			if (distance(realPointOf(object)) > 3) { return false; }
			ObjectComposition composition = SceneObjects.definitionOf(client, object);
			return activeTravel.matchesDeparture(object.getId(), realPointOf(object), 3)
				|| (composition != null && activeTravel.matchesDeparture(composition.getId(), realPointOf(object), 3));
		}
		if (onTile)
		{
			return standsOn(object);
		}
		if (!wantedIds.isEmpty() && wantedIds.contains(object.getId()))
		{
			return true;
		}
		// definitionOf resolves multi-state scenery through its impostor, and
		// must run on the client thread -- which every caller here already does.
		final ObjectComposition composition = SceneObjects.definitionOf(client, object);
		return composition != null
			&& (wantedIds.contains(composition.getId()) || nameMatches(composition.getName()));
	}

	// --- scene scanning ----------------------------------------------------

	/**
	 * Whether one of these is in the loaded scene, inside the box if given.
	 *
	 * <p>Answered for a quest's branch conditions -- "is the npc standing there
	 * yet", "has the object appeared", "is the item on the floor" -- which is
	 * how those branches notice the player has done something. The same walk
	 * this class already does for the current step, asked on someone else's
	 * behalf.
	 *
	 * <p>On the client thread, like everything else here: the caller reaches it
	 * from the game tick and from the panel, and the panel's answer is a tick
	 * stale rather than an assertion failure.
	 */
	public boolean isPresent(String kind, List<Integer> ids, List<List<Integer>> zone)
	{
		return conditionCheck().isPresent(kind, ids, zone);
	}

	/** A lazy, evaluation-local index; never retain transformed IDs across ticks. */
	public PresenceCheck conditionCheck()
	{
		return new PresenceCheck();
	}

	public final class PresenceCheck
	{
		private SceneObjects.PresenceIndex objectIndex;

		public boolean isPresent(String kind, List<Integer> ids, List<List<Integer>> zone)
		{
			if (client == null || ids.isEmpty()
				|| client.getGameState() != GameState.LOGGED_IN)
			{
				return false;
			}
			if ("npc".equals(kind))
			{
				for (NPC npc : client.getTopLevelWorldView().npcs())
				{
					if (npc != null && (ids.contains(npc.getId())
						|| composed(npc, ids)) && inside(zone, RealPoint.of(client, npc)))
					{
						return true;
					}
				}
				return false;
			}
			if ("object".equals(kind))
			{
				if (objectIndex == null)
				{
					objectIndex = new SceneObjects.PresenceIndex();
					SceneObjects.forEach(client, object ->
					{
						final WorldPoint point = RealPoint.of(client, object.getWorldLocation());
						objectIndex.add(object.getId(), point);
						final ObjectComposition definition = SceneObjects.definitionOf(client, object);
						if (definition != null && definition.getId() != object.getId())
						{
							objectIndex.add(definition.getId(), point);
						}
					});
				}
				return objectIndex.anyOf(ids, zone);
			}
			// A ground item. Asked of the tracker that already walks the tiles.
			return groundItems != null && groundItems.anyOf(ids, zone);
		}
	}

	private boolean composed(NPC npc, List<Integer> ids)
	{
		final NPCComposition composition = npc.getTransformedComposition();
		return composition != null && ids.contains(composition.getId());
	}

	/** Whether a point is in one of the boxes, or there are none to be in. */
	private static boolean inside(List<List<Integer>> zone, WorldPoint at)
	{
		if (zone == null || zone.isEmpty())
		{
			return true;
		}
		if (at == null)
		{
			return false;
		}
		for (List<Integer> box : zone)
		{
			if (box != null && box.size() >= 6
				&& box.get(0) <= at.getX() && at.getX() <= box.get(3)
				&& box.get(1) <= at.getY() && at.getY() <= box.get(4)
				&& box.get(2) <= at.getPlane() && at.getPlane() <= box.get(5))
			{
				return true;
			}
		}
		return false;
	}

	private void rescan()
	{
		npcs.clear();
		objects.clear();

		if (!isTracking() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (wantNpc)
		{
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (npc != null && npcMatches(npc))
				{
					npcs.add(npc);
				}
			}
		}

		if (wantObject)
		{
			SceneObjects.forEach(client, this::consider);
		}

		final int before = npcs.size() + objects.size();
		if (exactly)
		{
			// Quest Helper's coordinate, so Quest Helper's rules.
			keepOnTheTile(objects);
			keepWithinRoamRange(npcs);
		}
		else
		{
			keepNearest(npcs, NPC::getWorldLocation);
			keepNearest(objects, TileObject::getWorldLocation);
		}

	}

	/**
	 * Quest Helper's rule for scenery: the tile, not the neighbourhood.
	 *
	 * <p>{@code ObjectStep.addObjectToList} keeps an object when its own tile is
	 * the one the step names, or when the object stands over that tile -- a
	 * multi-tile object reports its centre, so a 3x3 door named by a corner has
	 * to be caught by its footprint. Only a step marked
	 * {@code showAllInArea} widens, and then to all of them within
	 * {@code maxObjectDistance}.
	 *
	 * <p>What this replaced kept whichever object of that id was nearest within
	 * twenty-five tiles. When the named one is in the scene the two agree -- it
	 * is nearest, at zero. They part when it is not: this keeps nothing, and
	 * nearest-within-25 highlighted a different barrel a few tiles away and sent
	 * the player to search it. There are five of that barrel's id around the
	 * mine cart.
	 */
	private void keepOnTheTile(List<TileObject> matches)
	{
		if (wantedAt.isEmpty())
		{
			return;
		}
		for (int i = matches.size() - 1; i >= 0; i--)
		{
			final TileObject object = matches.get(i);
			if (!standsOn(object) && !(spread && distance(realPointOf(object)) < FAR))
			{
				matches.remove(i);
			}
		}
	}

	/** Whether an object's own tile, or the tiles it covers, is the named one. */
	private boolean standsOn(TileObject object)
	{
		final WorldPoint at = realPointOf(object);
		if (at == null)
		{
			return false;
		}
		for (WorldPoint wanted : wantedAt)
		{
			if (wanted.equals(at))
			{
				return true;
			}
			if (!(object instanceof GameObject) || wanted.getPlane() != at.getPlane())
			{
				continue;
			}
			// A GameObject reports its centre tile, so walk back out to the
			// south-west corner before measuring the footprint. Quest Helper
			// does the same arithmetic, for the same reason.
			final GameObject game = (GameObject) object;
			final int west = at.getX() - (game.sizeX() - 1) / 2;
			final int south = at.getY() - (game.sizeY() - 1) / 2;
			if (wanted.getX() >= west && wanted.getX() < west + game.sizeX()
				&& wanted.getY() >= south && wanted.getY() < south + game.sizeY())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Quest Helper's rule for people: anywhere within roaming distance.
	 *
	 * <p>{@code NpcStep.addNpcToListGivenMatchingID} takes the npc when it is
	 * within {@code maxRoamRange} of the step's coordinate, and takes only one
	 * of them unless the step allows several. An npc is not where it was left --
	 * that is the whole reason the range exists -- and narrowing them the way
	 * scenery is narrowed dropped guards who had walked to the far end of their
	 * patrol.
	 *
	 * <p>One difference, deliberate: Quest Helper keeps whichever npc spawned
	 * first and this keeps the nearest. A rescan has no spawn order to keep.
	 */
	private void keepWithinRoamRange(List<NPC> matches)
	{
		if (wantedAt.isEmpty())
		{
			return;
		}
		int best = Integer.MAX_VALUE;
		final List<Integer> distances = new ArrayList<>(matches.size());
		for (NPC npc : matches)
		{
			final int away = distance(realPointOf(npc));
			distances.add(away);
			best = Math.min(best, away);
		}
		final int keep = spread ? ROAM - 1 : best;
		for (int i = matches.size() - 1; i >= 0; i--)
		{
			if (distances.get(i) > keep || distances.get(i) >= ROAM)
			{
				matches.remove(i);
			}
		}
	}

	/**
	 * Narrow a list of matches by where the step says the thing is.
	 *
	 * <p>For the guide's own coordinates, which are wiki map markers rather than
	 * tiles: a marker is dropped somewhere on the subject of a page and cannot
	 * be held to the square it landed on, so this keeps the nearest match and
	 * leaves a lone distant one alone rather than trading one highlight for
	 * none.
	 */
	private <T> void keepNearest(List<T> matches, Function<T, WorldPoint> whereItIs)
	{
		if (wantedAt.isEmpty() || matches.size() < 2)
		{
			return;
		}

		int best = Integer.MAX_VALUE;
		final List<Integer> distances = new ArrayList<>(matches.size());
		for (T match : matches)
		{
			final int away = distance(whereItIs.apply(match));
			distances.add(away);
			best = Math.min(best, away);
		}
		if (best > NEAR)
		{
			return;
		}

		final int keep = spread ? ROAM : best;
		for (int i = matches.size() - 1; i >= 0; i--)
		{
			if (distances.get(i) > keep)
			{
				matches.remove(i);
			}
		}
	}

	private WorldPoint realPointOf(NPC npc)
	{
		return RealPoint.of(client, npc);
	}

	private WorldPoint realPointOf(TileObject object)
	{
		return RealPoint.of(client, object.getWorldLocation());
	}

	/** Tiles from a point to the nearest place the step points at, or huge. */
	private int distance(WorldPoint at)
	{
		if (at == null)
		{
			return Integer.MAX_VALUE;
		}
		int best = Integer.MAX_VALUE;
		for (WorldPoint wanted : wantedAt)
		{
			if (wanted.getPlane() != at.getPlane())
			{
				continue;
			}
			best = Math.min(best, wanted.distanceTo2D(at));
		}
		return best;
	}

	/** Three-number coordinates as world points, skipping any that are not. */
	private static List<WorldPoint> points(List<List<Integer>> raw)
	{
		final List<WorldPoint> out = new ArrayList<>();
		for (List<Integer> one : raw)
		{
			final WorldPoint at = pointOf(one);
			if (at != null)
			{
				out.add(at);
			}
		}
		return out;
	}

	private static WorldPoint pointOf(List<Integer> raw)
	{
		return raw == null || raw.size() < 3
			? null
			: new WorldPoint(raw.get(0), raw.get(1), raw.get(2));
	}

	private void consider(TileObject object)
	{
		// Spawn events can repeat a scan's match. Identity, not id: several
		// separate crates may intentionally share an id and all need outlining.
		for (TileObject existing : objects)
		{
			if (existing == object)
			{
				return;
			}
		}
		if (object != null && objects.size() < 64 && objectMatches(object))
		{
			objects.add(object);
		}
	}

	// --- events ------------------------------------------------------------

	public void onNpcSpawned(NpcSpawned event)
	{
		if (wantNpc && npcMatches(event.getNpc()))
		{
			npcs.add(event.getNpc());
		}
	}

	public void onNpcDespawned(NpcDespawned event)
	{
		npcs.remove(event.getNpc());
	}

	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		consider(event.getGameObject());
	}

	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		objects.remove(event.getGameObject());
	}

	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		consider(event.getWallObject());
	}

	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		consider(event.getGroundObject());
	}

	public void onGameStateChanged(GameStateChanged event)
	{
		// The scene is rebuilt on every loading screen and hop, so anything we
		// were holding is stale.
		if (event.getGameState() == GameState.LOADING
			|| event.getGameState() == GameState.HOPPING
			|| event.getGameState() == GameState.LOGIN_SCREEN)
		{
			npcs.clear();
			objects.clear();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::rescan);
		}
	}
}
