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

import com.b0atyguide.data.Destination;
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import com.b0atyguide.overlay.ApproachTracker;
import com.b0atyguide.overlay.SceneTracker;
import java.awt.Point;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.Constants;
import net.runelite.api.ObjectComposition;
import net.runelite.api.GameObject;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import com.b0atyguide.overlay.SceneObjects;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Keeps a current walkable route from the player to the step's target.
 *
 * <p>Recomputed when the player or target moves and when scene-object state
 * changes, not per frame: the search is cheap but an overlay renders many
 * times a tick.
 */
@Singleton
public class PathTracker
{
	@Inject
	private Client client;

	@Inject
	private SceneTracker tracker;

	@Inject
	private ApproachTracker approachTracker;

	private List<Point> path = Collections.emptyList();
	private WorldPoint lastFrom;
	private WorldPoint lastTo;
	private TileObject destinationObject;
	private NPC destinationNpc;
	private String targetFootprint = "none";

	/** Scene-local tiles from the player to the target, or empty. */
	public List<Point> getPath()
	{
		return path;
	}

	/** The pathfinding implementation in this running Java build. */
	public String getRoutingRevision()
	{
		return PathFinder.ROUTING_REVISION;
	}

	/** Actual loaded size used for reaching the destination, not its centre. */
	public String getTargetFootprint()
	{
		return targetFootprint;
	}

	// --- what it decided, for the diagnostic ---------------------------------

	/** The scene the door mask was built for, so it is built once per scene. */
	private Object doorsFor;
	private int doorsPlane = -1;
	private int[][] cachedDoors;
	private int[][] cachedWays;
	private int[][] cachedVerifiedEntries;

	/**
	 * The way into the enclosure the destination sits in, or null.
	 *
	 * <p>Set when a door, gate or stile is the next action on the best route,
	 * even if a longer strict route could walk around it. The overlay marks it:
	 * a line that stops at a gate does not, on its own, say which gate.
	 */
	private WorldPoint entrance;
	private TileObject entranceObject;

	/** Live geometry is projected by the renderer; finding the object is tick work. */
	public TileObject getEntranceObject()
	{
		return entranceObject;
	}

	/*
	 * The diagnostic overlay needs to distinguish "there was no gate in the
	 * loaded scene" from "there was a gate, but it did not bridge the two
	 * collision regions".  Those failures look identical as just a line to a
	 * wall, but require entirely different fixes.
	 */
	private int entryCandidates;
	private WorldPoint nearestEntryCandidate;
	private int actionableEntryCandidates;
	private WorldPoint nearestActionableEntry;
	private int nearestActionableDirections;
	private int verifiedEntryCandidates;
	private WorldPoint nearestVerifiedEntry;
	private int nearestVerifiedDirections;
	private String routeDecision = "none";
	private String entryGraphCheck = "not run";

	/** Where the route is heading, or null. */
	public WorldPoint getDestination()
	{
		return lastTo;
	}

	/**
	 * Which rule produced that destination, in plain words.
	 *
	 * <p>Seven rules answer in order and the wrong one winning is invisible from
	 * inside the client -- the line just goes somewhere odd.
	 */
	public String getReason()
	{
		return reason;
	}

	/**
	 * Record which rule answered, and hand the answer straight back.
	 *
	 * <p>Wrapped rather than set separately so a new rule cannot be added
	 * without naming itself.
	 */
	private WorldPoint said(String rule, WorldPoint at)
	{
		reason = at == null ? "nothing" : rule;
		return at;
	}

	private String reason = "nothing";

	public void clear()
	{
		path = Collections.emptyList();
		lastFrom = null;
		lastTo = null;
		entrance = null;
		destinationObject = null;
		destinationNpc = null;
		targetFootprint = "none";
		entryCandidates = 0;
		nearestEntryCandidate = null;
		actionableEntryCandidates = 0;
		nearestActionableEntry = null;
		nearestActionableDirections = 0;
		verifiedEntryCandidates = 0;
		nearestVerifiedEntry = null;
		nearestVerifiedDirections = 0;
		routeDecision = "none";
		entryGraphCheck = "not run";
		// Scene-sized and held on a singleton, so it outlives a plugin toggle
		// if it is not dropped here as well as on a scene change.
		onSceneChanged();
	}

	/**
	 * The door the route is stopping at, in world coordinates, or null.
	 *
	 * <p>Read by the highlight overlay. Null whenever the destination itself
	 * could be reached, which is the ordinary case.
	 */
	public WorldPoint getEntrance()
	{
		return entrance;
	}

	/** Number of loaded objects that could be an entry for this route. */
	public int getEntryCandidates()
	{
		return entryCandidates;
	}

	/** Closest loaded entry candidate to the destination, for diagnostics. */
	public WorldPoint getNearestEntryCandidate()
	{
		return nearestEntryCandidate;
	}

	/** Number of candidates backed by a live traversal action such as Open. */
	public int getActionableEntryCandidates()
	{
		return actionableEntryCandidates;
	}

	/** Closest action-backed entry to the destination, for diagnostics. */
	public WorldPoint getNearestActionableEntry()
	{
		return nearestActionableEntry;
	}

	/** Live collision edges relaxed for the nearest actionable entry. */
	public String getNearestActionableDirections()
	{
		return directions(nearestActionableDirections);
	}

	/** Number of live action-backed entries with an explicit transport edge. */
	public int getVerifiedEntryCandidates()
	{
		return verifiedEntryCandidates;
	}

	/** Closest verified live entry to the destination, for diagnostics. */
	public WorldPoint getNearestVerifiedEntry()
	{
		return nearestVerifiedEntry;
	}

	/** Verified transport edges for the closest such entry. */
	public String getNearestVerifiedDirections()
	{
		return directions(nearestVerifiedDirections);
	}

	/** The PathFinder branch which produced the currently drawn line. */
	public String getRouteDecision()
	{
		return routeDecision;
	}

	/** What happened when verified or live-action edges were added to the graph. */
	public String getEntryGraphCheck()
	{
		return entryGraphCheck;
	}

	private static String directions(int value)
	{
		final StringBuilder directions = new StringBuilder(4);
		if ((value & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0)
		{
			directions.append('N');
		}
		if ((value & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0)
		{
			directions.append('E');
		}
		if ((value & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0)
		{
			directions.append('S');
		}
		if ((value & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0)
		{
			directions.append('W');
		}
		return directions.toString();
	}

	/**
	 * A scene point as a world point, or null.
	 *
	 * <p>Through the instance correction like every other position here, so a
	 * gate found inside an instanced region is named in the coordinates the
	 * rest of the plugin speaks.
	 */
	private WorldPoint worldPointOf(WorldView view, java.awt.Point at)
	{
		if (at == null)
		{
			return null;
		}
		final LocalPoint local = LocalPoint.fromScene(at.x, at.y, view);
		return local == null ? null : WorldPoint.fromLocalInstance(client, local);
	}

	/** Drop the cached scene data. Called when the scene is replaced. */
	public void onSceneChanged()
	{
		entranceObject = null;
		doorsFor = null;
		cachedDoors = null;
		cachedWays = null;
		cachedVerifiedEntries = null;
		doorsPlane = -1;
	}

	/**
	 * Drop the cached entry scan when scenery changes inside the same scene.
	 *
	 * <p>Opening and closing a door replaces its scene object without replacing
	 * the {@code Scene}. The earlier cache therefore kept whichever action was
	 * present when it was first built: a gate first seen open was missing after
	 * it closed, while a despawned gate could remain eligible. Mark the route
	 * dirty as well. Otherwise {@link #update()} sees the same player and target
	 * coordinates and returns before rebuilding the invalidated scan.
	 */
	public void onSceneObjectChanged()
	{
		onSceneChanged();
		lastFrom = null;
	}

	/** Call from a game tick. */
	public void update()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			clear();
			return;
		}

		final WorldPoint from = RealPoint.of(client, client.getLocalPlayer());
		final WorldPoint to = from == null ? null : destination(from);
		// A different *level* is a real obstacle -- a cellar is reached by its
		// trapdoor and nothing else -- and the approach tracker answers that.
		// A different plane in the same place is not: "Head to the Grand Tree"
		// names a spot on its first floor, and the walk across Gnome Stronghold
		// to get under it is ordinary ground-level walking. Comparing planes
		// refused to draw that walk at all.
		if (from == null || to == null || levelOf(from) != levelOf(to))
		{
			clear();
			return;
		}

		// Standing still is normally reason enough to keep the last answer. It
		// is not while the route is stopped at something shut: opening a gate
		// changes the collision map without moving the player one tile, and the
		// line would go on ending at a gate that is now open, with the gate
		// still ringed. Recomputing while an entry is named costs one route on
		// the ticks where the player is waiting to act on it.
		if (entrance == null && from.equals(lastFrom) && to.equals(lastTo))
		{
			return;
		}
		lastFrom = from;
		lastTo = to;

		final WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			clear();
			return;
		}
		final CollisionData[] maps = view.getCollisionMaps();
		if (maps == null || view.getPlane() < 0 || view.getPlane() >= maps.length || maps[view.getPlane()] == null)
		{
			// Do not cache a failed attempt as a route. When collision arrives,
			// the same stationary player/target must be retried on the next tick.
			clear();
			return;
		}

		// The player's live local position stays correct in rotated instances;
		// the canonical wiki coordinate cannot be converted back with fromWorld.
		final LocalPoint start = client.getLocalPlayer().getLocalLocation();
		if (start == null)
		{
			clear();
			return;
		}

		// A destination past the loaded scene used to draw nothing at all, so
		// "Bank at Varrock West" showed a line only once the booths themselves
		// loaded. Routing to the edge of the scene in the right direction is
		// still true -- it is the part of the walk the client can actually see
		// -- and it is what the player needs while crossing the map.
		LocalPoint end = destinationObject != null ? destinationObject.getLocalLocation()
			: destinationNpc != null ? destinationNpc.getLocalLocation()
			: LocalPoint.fromWorld(view, to);
		if (end == null)
		{
			end = LocalPoint.fromWorld(view, clampToScene(view, from, to));
		}
		if (end == null)
		{
			clear();
			return;
		}

		final int[][] passable = doors(view);
		describeEntryCandidates(view, to);
		final PathTarget target = sceneTarget(view, end, maps[view.getPlane()].getFlags());
		targetFootprint = target.describe();
		final PathFinder.Route route = PathFinder.routeTo(
			maps[view.getPlane()].getFlags(),
			passable, cachedWays, cachedVerifiedEntries,
			start.getSceneX(), start.getSceneY(), target);
		path = route.getPath();
		routeDecision = route.getDecision();
		entryGraphCheck = route.getGraphCheck();
		// Taken from the route every time. Holding the previous answer when the
		// new route has none is how a ring stays on a gate the player has
		// already opened and walked through.
		entrance = worldPointOf(view, route.getEntrance());
		entranceObject = entryObjectAt(view, route.getEntrance());

	}

	/**
	 * Resolve the selected entry only when a route changes, not by scanning the
	 * entire scene each rendered frame. The mask came from this exact scene tile;
	 * inspect its four object categories and retain only a live traversal action.
	 * This also outlines a multi-tile game object when the selected crossing is
	 * on its footprint rather than on the centre returned by getWorldLocation.
	 */
	private TileObject entryObjectAt(WorldView view, Point at)
	{
		if (at == null)
		{
			return null;
		}
		final Tile[][][] tiles = view.getScene().getTiles();
		final int plane = view.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null
			|| tiles[plane][at.x] == null)
		{
			return null;
		}
		final Tile tile = tiles[plane][at.x][at.y];
		if (tile == null)
		{
			return null;
		}
		if (isActionableEntry(tile.getWallObject()))
		{
			return tile.getWallObject();
		}
		if (isActionableEntry(tile.getDecorativeObject()))
		{
			return tile.getDecorativeObject();
		}
		if (isActionableEntry(tile.getGroundObject()))
		{
			return tile.getGroundObject();
		}
		final GameObject[] objects = tile.getGameObjects();
		if (objects != null)
		{
			for (GameObject object : objects)
			{
				if (isActionableEntry(object))
				{
					return object;
				}
			}
		}
		return null;
	}

	/**
	 * Keep the selected entity's real bounds all the way into the solver.
	 *
	 * <p>GameObject world locations name their centre. Scene min/max already
	 * account for rotation and instance placement and are the collision footprint
	 * we actually need. The same object appears on every occupied scene tile;
	 * nine matches can therefore be one 3x3 cannon, not nine separate targets.
	 * NPCs also have sizes greater than one and supply their own WorldArea.
	 */
	private PathTarget sceneTarget(WorldView view, LocalPoint end, int[][] flags)
	{
		if (destinationObject instanceof GameObject)
		{
			final GameObject object = (GameObject) destinationObject;
			final net.runelite.api.Point min = object.getSceneMinLocation();
			final net.runelite.api.Point max = object.getSceneMaxLocation();
			if (min != null && max != null)
			{
				return PathTarget.object(min.getX(), min.getY(), max.getX(), max.getY());
			}
		}
		if (destinationNpc != null)
		{
			final WorldArea area = destinationNpc.getWorldArea();
			final LocalPoint min = area == null ? null
				: LocalPoint.fromWorld(view, area.toWorldPoint());
			if (min != null)
			{
				return PathTarget.object(min.getSceneX(), min.getSceneY(),
					min.getSceneX() + area.getWidth() - 1,
					min.getSceneY() + area.getHeight() - 1);
			}
		}
		if (destinationObject instanceof WallObject)
		{
			return PathTarget.wall(end.getSceneX(), end.getSceneY());
		}
		if (destinationObject != null || destinationNpc != null)
		{
			return PathTarget.object(end.getSceneX(), end.getSceneY(),
				end.getSceneX(), end.getSceneY());
		}
		return PathTarget.location(flags, end.getSceneX(), end.getSceneY());
	}

	/**
	 * Summarise the scene's possible entries without choosing one.  This is
	 * intentionally separate from PathFinder: it exposes what the client gave
	 * us, not a second interpretation of the routing algorithm.
	 */
	private void describeEntryCandidates(WorldView view, WorldPoint target)
	{
		entryCandidates = 0;
		nearestEntryCandidate = null;
		actionableEntryCandidates = 0;
		nearestActionableEntry = null;
		nearestActionableDirections = 0;
		verifiedEntryCandidates = 0;
		nearestVerifiedEntry = null;
		nearestVerifiedDirections = 0;
		if (cachedWays == null || target == null)
		{
			return;
		}

		int nearestDistance = Integer.MAX_VALUE;
		int nearestActionableDistance = Integer.MAX_VALUE;
		int nearestVerifiedDistance = Integer.MAX_VALUE;
		for (int x = 0; x < Constants.SCENE_SIZE; x++)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; y++)
			{
				if (cachedWays[x][y] == 0)
				{
					continue;
				}
				entryCandidates++;
				final WorldPoint candidate = worldPointOf(view, new Point(x, y));
				if (candidate == null || candidate.getPlane() != target.getPlane())
				{
					continue;
				}
				final int distance = Math.abs(candidate.getX() - target.getX())
					+ Math.abs(candidate.getY() - target.getY());
				if (distance < nearestDistance)
				{
					nearestDistance = distance;
					nearestEntryCandidate = candidate;
				}
				if (cachedDoors != null && cachedDoors[x][y] != 0)
				{
					actionableEntryCandidates++;
					if (distance < nearestActionableDistance)
					{
						nearestActionableDistance = distance;
						nearestActionableEntry = candidate;
						nearestActionableDirections = cachedDoors[x][y];
					}
				}
				if (cachedVerifiedEntries != null && cachedVerifiedEntries[x][y] != 0)
				{
					verifiedEntryCandidates++;
					if (distance < nearestVerifiedDistance)
					{
						nearestVerifiedDistance = distance;
						nearestVerifiedEntry = candidate;
						nearestVerifiedDirections = cachedVerifiedEntries[x][y];
					}
				}
			}
		}
	}

	/**
	 * The far destination, pulled back to the last tile inside the scene.
	 *
	 * <p>Walks the straight line from the player towards it and takes the last
	 * point still loaded, so the route heads the right way rather than not
	 * existing. Deliberately not a guess about the rest of the journey: the
	 * line stops where the client's knowledge does.
	 */
	private static WorldPoint clampToScene(WorldView view, WorldPoint from, WorldPoint to)
	{
		final int steps = Math.max(
			Math.abs(to.getX() - from.getX()), Math.abs(to.getY() - from.getY()));
		if (steps == 0)
		{
			return from;
		}

		WorldPoint best = from;
		for (int i = 1; i <= steps; i++)
		{
			final int x = from.getX() + (to.getX() - from.getX()) * i / steps;
			final int y = from.getY() + (to.getY() - from.getY()) * i / steps;
			final WorldPoint at = new WorldPoint(x, y, from.getPlane());
			if (LocalPoint.fromWorld(view, at) == null)
			{
				break;
			}
			best = at;
		}
		return best;
	}

	/**
	 * Whether anything on this tile looks like a way into somewhere.
	 *
	 * <p>Wider than {@link #passableDirections}, and used for a different
	 * question. That one decides where the route may walk, so it only counts
	 * what is certainly passable. This one answers "the destination is walled
	 * off -- which of these is the way in", and the answer is drawn on screen
	 * for the player to judge rather than walked through. "Enter" belongs here
	 * and not there for exactly that reason: it is how you get into an
	 * enclosure, and also how you get into a building you were not going into.
	 */
	private int waysIn(Tile tile, int certain)
	{
		if (certain != 0)
		{
			return certain;
		}
		if (looksLikeAWayIn(tile.getWallObject())
			|| looksLikeAWayIn(tile.getDecorativeObject())
			|| looksLikeAWayIn(tile.getGroundObject()))
		{
			return EVERY_SIDE;
		}
		final GameObject[] objects = tile.getGameObjects();
		if (objects != null)
		{
			for (GameObject object : objects)
			{
				if (looksLikeAWayIn(object))
				{
					return EVERY_SIDE;
				}
			}
		}
		return 0;
	}

	private boolean looksLikeAWayIn(TileObject object)
	{
		if (object == null)
		{
			return false;
		}
		if (offersAnyAction(object, ENTRY_ACTIONS))
		{
			return true;
		}

		final ObjectComposition raw = client.getObjectDefinition(object.getId());
		final ObjectComposition resolved = SceneObjects.definitionOf(client, object);
		if (raw == null && resolved == null)
		{
			return false;
		}
		return entryName(raw) || (resolved != raw && entryName(resolved));
	}

	private static boolean entryName(ObjectComposition definition)
	{
		if (definition == null)
		{
			return false;
		}
		final String name = definition.getName();
		return name != null && ENTRY_NAMES.matcher(name).find();
	}

	/** What a way into somewhere offers, beyond what can simply be walked. */
	private static final String[] ENTRY_ACTIONS = {
		"Open", "Slash", "Enter", "Unlock", "Pick-lock", "Push"
	};

	private static final String EDGE = String.valueOf((char) 92) + "b";

	/**
	 * And what one is called, for the ones whose action says nothing useful.
	 *
	 * <p>The word boundary is built from a character rather than written as
	 * an escape: a literal backslash in a Java string in a generated file is
	 * one more thing to get wrong, and it has been got wrong before.
	 */
	private static final java.util.regex.Pattern ENTRY_NAMES =
		java.util.regex.Pattern.compile("(?i)" + EDGE
			+ "(gate|door|stile|fence|web|entrance|archway)" + EDGE);

	/**
	 * Whether anything on this tile can simply be walked through.
	 *
	 * <p>Not only wall objects. A gate is often a game object rather than a
	 * wall, so looking at walls alone left the route refusing to cross the one
	 * outside Taverley -- and a fence offers "Climb-over" or "Squeeze-through"
	 * rather than "Open", so checking that single action missed those too.
	 *
	 * <p>Every verb here means "you can get past this". Deliberately not
	 * "Enter": that is a doorway into somewhere else, and treating it as a
	 * shortcut would route the player through a building they were not going
	 * into.
	 */
	private int passableDirections(Tile tile, int[][] collision)
	{
		// A door is a wall that opens, and it opens in one direction. Saying
		// only "this tile has a door on it" let the route ignore every wall
		// touching that tile, so a doorway in the corner of a room was a hole
		// in two walls -- the line went straight through the stonework beside
		// the door, which is what the guide's author kept walking into.
		final WallObject wall = tile.getWallObject();
		if (openable(wall))
		{
			final int orientation = sideOf(wall.getOrientationA())
				| sideOf(wall.getOrientationB());
			final LocalPoint at = tile.getLocalLocation();
			if (at != null)
			{
				final int blocked = collisionSides(collision,
					at.getSceneX(), at.getSceneY(), orientation);
				if (blocked != 0)
				{
					return blocked;
				}
			}
			return orientation;
		}

		// A gate is often a game object rather than a wall, and a fence offers
		// "Climb-over" rather than "Open". Those stand alone in a fence line
		// with no other wall on their tile, so there is nothing on the tile for
		// a whole-tile relaxation to punch a hole through.
		if (openable(tile.getDecorativeObject()) || openable(tile.getGroundObject()))
		{
			return EVERY_SIDE;
		}
		final GameObject[] objects = tile.getGameObjects();
		if (objects != null)
		{
			for (GameObject object : objects)
			{
				if (openable(object))
				{
					return EVERY_SIDE;
				}
			}
		}
		return 0;
	}

	/** The bitfield reading, for a test. */
	static int sidesOfForTest(int orientation)
	{
		return sideOf(orientation);
	}

	/** The collision edge selected for an oriented wall, for a test. */
	static int collisionSidesForTest(int[][] collision, int x, int y, int orientation)
	{
		return collisionSides(collision, x, y, sideOf(orientation));
	}

	/**
	 * Which edge beside a wall is actually blocked in the live collision map.
	 *
	 * <p>A wall's model orientation establishes its north/south or east/west
	 * axis, but not reliably which side of the owning tile holds the crossing.
	 * Ask both sides of each candidate edge: the client can record a wall on
	 * either tile under opposite movement flags.
	 */
	private static int collisionSides(int[][] flags, int x, int y, int orientation)
	{
		if (flags == null || x < 0 || y < 0 || x >= flags.length
			|| flags[x] == null || y >= flags[x].length)
		{
			return 0;
		}

		int axis = 0;
		if ((orientation & (CollisionDataFlag.BLOCK_MOVEMENT_NORTH
			| CollisionDataFlag.BLOCK_MOVEMENT_SOUTH)) != 0)
		{
			axis |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH
				| CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		}
		if ((orientation & (CollisionDataFlag.BLOCK_MOVEMENT_EAST
			| CollisionDataFlag.BLOCK_MOVEMENT_WEST)) != 0)
		{
			axis |= CollisionDataFlag.BLOCK_MOVEMENT_EAST
				| CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}

		int blocked = flags[x][y] & EVERY_SIDE;
		if (y + 1 < flags[x].length
			&& (flags[x][y + 1] & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0)
		{
			blocked |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
		}
		if (y > 0
			&& (flags[x][y - 1] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0)
		{
			blocked |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		}
		if (x + 1 < flags.length && flags[x + 1] != null
			&& y < flags[x + 1].length
			&& (flags[x + 1][y] & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0)
		{
			blocked |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		}
		if (x > 0 && flags[x - 1] != null && y < flags[x - 1].length
			&& (flags[x - 1][y] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0)
		{
			blocked |= CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		return blocked & axis;
	}

	/**
	 * The movement flags a wall of this orientation blocks.
	 *
	 * <p><b>A bitfield</b>, which is the whole point: 1 west, 2 north, 4 east,
	 * 8 south, and the higher bits diagonals. A gate in the corner of a fence
	 * carries two of them at once -- west and north is 3 -- and reading it as a
	 * single value matched none of the four and so opened nothing. Every gate
	 * and stile that sits on a corner stopped being passable, which is the half
	 * of this that made the routes worse rather than better.
	 *
	 * <p>The diagonal bits have no movement flag to relax and are ignored: a
	 * diagonal wall is crossed by going round it.
	 */
	private static int sideOf(int orientation)
	{
		int sides = 0;
		if ((orientation & 1) != 0)
		{
			sides |= CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		if ((orientation & 2) != 0)
		{
			sides |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
		}
		if ((orientation & 4) != 0)
		{
			sides |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		}
		if ((orientation & 8) != 0)
		{
			sides |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		}
		return sides;
	}

	private static final int EVERY_SIDE =
		CollisionDataFlag.BLOCK_MOVEMENT_NORTH | CollisionDataFlag.BLOCK_MOVEMENT_EAST
			| CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_WEST;

	/** Whether this object offers a way through. */
	private boolean openable(TileObject object)
	{
		return offersAnyAction(object, PASSABLE_ACTIONS);
	}

	/**
	 * Whether this loaded object is something the entrance highlight may mark.
	 *
	 * <p>An entry is carried through the pure pathfinder as a coordinate. More
	 * than one object can occupy that tile, so the renderer must ask this again
	 * rather than outline the first object there, which might be an Inspect-only
	 * perimeter wall sharing the gate's anchor.
	 */
	public boolean isActionableEntry(TileObject object)
	{
		return openable(object);
	}

	/**
	 * Whether either representation of a stateful object offers an action.
	 *
	 * <p>The right-click menu is built from the live object's raw definition,
	 * while many stateful objects also expose an impostor/transformed
	 * definition. Some gates keep {@code Open} only on the raw wall ID and
	 * transform to a placeholder definition for their other state. Looking at
	 * only the impostor made a visible Open gate name-only rather than an
	 * actionable collision boundary.
	 */
	private boolean offersAnyAction(TileObject object, String[] verbs)
	{
		if (object == null)
		{
			return false;
		}
		final ObjectComposition raw = client.getObjectDefinition(object.getId());
		final ObjectComposition resolved = SceneObjects.definitionOf(client, object);
		return offersAnyAction(raw, verbs)
			|| (resolved != raw && offersAnyAction(resolved, verbs));
	}

	private static boolean offersAnyAction(ObjectComposition definition, String[] verbs)
	{
		if (definition == null)
		{
			return false;
		}
		for (String verb : verbs)
		{
			if (SceneObjects.hasAction(definition.getActions(), verb, null))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * What a door, gate, fence or stile offers when it can be crossed.
	 *
	 * <p>Deliberately not "Enter": that is a doorway into somewhere else, and
	 * treating it as a shortcut routes the player through a building they were
	 * not going into.
	 */
	private static final String[] PASSABLE_ACTIONS = {
		"Open", "Slash", "Pass", "Pass-through", "Go-through", "Walk-through",
		"Climb-through", "Climb-over", "Climb over", "Climb-under",
		"Squeeze-through", "Squeeze-past", "Jump-over", "Jump over", "Cross",
	};

	/**
	 * The ways through, cached until the scene changes.
	 *
	 * <p>Building it walks all 104x104 tiles and asks the client for a
	 * definition per object on each -- and it was being rebuilt on every tick
	 * the player took a step, for a scene that had not changed. The doors are a
	 * property of the loaded scene, so they are computed when that loads and
	 * kept until it loads again.
	 *
	 * <p>Opening or closing a door replaces its scene object and invalidates
	 * this cache through {@link #onSceneObjectChanged()}. The collision flags
	 * are read live too, but that is not sufficient: the replacement changes
	 * whether the object offers Open and therefore whether it belongs in the
	 * verified transport graph.
	 */
	private int[][] doors(WorldView view)
	{
		if (doorsFor == view.getScene() && doorsPlane == view.getPlane())
		{
			return cachedDoors;
		}
		buildDoorMasks(view);
		cachedVerifiedEntries = null;
		applyKnownEntryDirections(view);
		doorsFor = view.getScene();
		doorsPlane = view.getPlane();
		return cachedDoors;
	}

	/**
	 * Replace ambiguous live wall orientation with verified crossing directions.
	 *
	 * <p>The external table is never enough on its own: a direction is applied
	 * only where the current scene independently found an object offering an
	 * allowed crossing action such as Open or Slash. Thus a perimeter wall
	 * offering only Inspect remains solid even if it shares a tile with a known
	 * transport.
	 */
	private void applyKnownEntryDirections(WorldView view)
	{
		if (cachedDoors == null)
		{
			return;
		}
		for (int x = 0; x < Constants.SCENE_SIZE; x++)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; y++)
			{
				if (cachedDoors[x][y] == 0)
				{
					continue;
				}
				final WorldPoint point = worldPointOf(view, new Point(x, y));
				final int known = KnownEntryTransports.directionsAt(point);
				if (known == 0)
				{
					continue;
				}
				if (cachedVerifiedEntries == null)
				{
					cachedVerifiedEntries =
						new int[Constants.SCENE_SIZE][Constants.SCENE_SIZE];
				}
				cachedVerifiedEntries[x][y] = known;
				cachedDoors[x][y] |= known;
			}
		}
	}

	/**
	 * Scene tiles holding a door.
	 *
	 * <p>A shut door carries the same collision flags as a wall, so without
	 * this the route simply refuses to cross one and the player is left with no
	 * line at all. Opening a door is a normal part of walking somewhere.
	 *
	 * <p>Recognised by the object's own actions rather than by a list of ids:
	 * there are hundreds of door ids and the set grows with every update, but
	 * "Open" is what a door offers.
	 */
	private void buildDoorMasks(WorldView view)
	{
		cachedDoors = null;
		cachedWays = null;
		final Tile[][][] tiles = view.getScene().getTiles();
		final int plane = view.getPlane();
		if (tiles == null || plane >= tiles.length || tiles[plane] == null)
		{
			return;
		}

		final CollisionData[] maps = view.getCollisionMaps();
		final int[][] collision = maps != null && plane < maps.length && maps[plane] != null
			? maps[plane].getFlags() : null;
		for (Tile[] column : tiles[plane])
		{
			if (column == null)
			{
				continue;
			}
			for (Tile tile : column)
			{
				if (tile == null)
				{
					continue;
				}
				// Compute actions/orientation once. The previous two scene passes
				// also recomputed passableDirections inside the wider candidate pass.
				final int through = passableDirections(tile, collision);
				final int possible = waysIn(tile, through);
				if (possible == 0)
				{
					continue;
				}
				final LocalPoint at = tile.getLocalLocation();
				if (at != null)
				{
					if (cachedWays == null)
					{
						cachedWays = new int[Constants.SCENE_SIZE][Constants.SCENE_SIZE];
					}
					cachedWays[at.getSceneX()][at.getSceneY()] |= possible;
					if (through != 0)
					{
						if (cachedDoors == null)
						{
							cachedDoors = new int[Constants.SCENE_SIZE][Constants.SCENE_SIZE];
						}
						cachedDoors[at.getSceneX()][at.getSceneY()] |= through;
					}
				}
			}
		}
	}

	/**
	 * How far up or down a point is, counting the underground as below ground.
	 *
	 * <p>The same rule {@code ApproachTracker} uses, and for the same reason: a
	 * basement is not a different plane in this game, it is the same plane in a
	 * band of the map far to the north.
	 */
	public static int levelOf(WorldPoint point)
	{
		return point.getY() >= UNDERGROUND_Y ? -1 : 0;
	}

	/** Where the underground band begins; no surface ground reaches it. */
	private static final int UNDERGROUND_Y = 6400;

	/** A three-number coordinate as a WorldPoint, or null when it is not one. */
	private static WorldPoint pointOf(List<Integer> raw)
	{
		return raw == null || raw.size() < 3
			? null
			: new WorldPoint(raw.get(0), raw.get(1), raw.get(2));
	}

	/**
	 * Where the player is being sent. A loaded NPC or object beats the wiki
	 * coordinate, since it is where the thing actually is right now.
	 */
	private WorldPoint destination(WorldPoint from)
	{
		reason = "nothing";
		destinationObject = null;
		destinationNpc = null;
		final Step step = tracker.getStep();
		if (step == null)
		{
			return null;
		}

		final Target target = step.getTarget();

		// On another floor there is no walkable route to the target itself, so
		// the route that matters is the one to the stairs.
		final TileObject approach = approachTracker.getApproach();
		if (approach != null)
		{
			destinationObject = approach;
			return said("the way up or down", RealPoint.of(client, approach.getWorldLocation()));
		}

		// Quest Helper's coordinate before the guide's anything, where it is
		// speaking: a loaded match below is now its own thing, because the scene
		// tracker stops looking for the guide's target once an instruction is in
		// force -- but the guide's destination further down is still the town
		// the step mentions, and that is the vaguer answer on a quest step.
		final List<NPC> npcs = tracker.getNpcs();
		if (!npcs.isEmpty())
		{
			destinationNpc = npcs.get(0);
			return said("an npc in the scene", RealPoint.of(client, destinationNpc));
		}
		final List<TileObject> objects = tracker.getObjects();
		if (!objects.isEmpty())
		{
			destinationObject = objects.get(0);
			return said("scenery in the scene",
				RealPoint.of(client, destinationObject.getWorldLocation()));
		}

		// Quest Helper's own coordinate for what the game is waiting on. Ahead
		// of the guide's target because on a quest step the guide names the
		// quest ("continue Rune Mysteries") while this names the place.
		final QuestHelperSteps.Instruction instruction = routingInstruction(step, tracker.getInstruction());
		if (instruction != null)
		{
			final WorldPoint at = pointOf(instruction.getPoint());
			if (at != null)
			{
				return said("quest helper's coordinate", at);
			}
		}

		// Board the transport before aiming at its arrival town. Loaded actors
		// above still win, but an unloaded captain must not erase the dock.
		if (step.getTravel() != null && (target == null || !target.isHighlightable())
			&& instruction == null)
		{
			return said("the departure dock", step.getTravel().nearestOrigin(from));
		}

		// Not when they are scattered: taking the first of ninety-one
		// coordinates spread over the world is a route to whichever one the
		// pipeline happened to list first, drawn with the same confidence as
		// one that is actually known.
		if (target != null && !target.isScattered())
		{
			for (List<Integer> raw : target.getPoints())
			{
				if (raw != null && raw.size() >= 3)
				{
					return said("the target's own coordinate",
						new WorldPoint(raw.get(0), raw.get(1), raw.get(2)));
				}
			}
		}

		// "Travel to Port Piscarilius" names a place, not a thing in the scene.
		// The world map has always marked these from the step's destination;
		// this asked only the target, so the marker appeared with no line under
		// it. Last, because a loaded NPC is a better answer than a coordinate.
		final Destination place = step.getDestination();
		if (place != null && !place.isAmbiguous())
		{
			for (List<Integer> raw : place.getPoints())
			{
				if (raw != null && raw.size() >= 3)
				{
					return said("the place the step names",
						new WorldPoint(raw.get(0), raw.get(1), raw.get(2)));
				}
			}
		}

		// Last: the nearest shop that sells what the step says to buy. Only when
		// the guide named no place of its own -- "Buy 2x Bronze Med Helm in
		// Barbarian Village" says where to go and must not be answered with a
		// shop in Varrock because the player happens to be standing in it.
		return said("the nearest shop that sells it", nearestSeller(step, from));
	}

	/**
	 * Whichever shopkeeper is closest, or null when the step names none.
	 *
	 * <p>Straight-line distance, not walking distance: the point of this is to
	 * choose between shops in different cities, and at that range the two agree.
	 */
	static QuestHelperSteps.Instruction routingInstruction(Step step,
		QuestHelperSteps.Instruction instruction)
	{
		// Scene matching and map markers give an explicitly named guide target
		// priority. Keep that priority when it is outside the loaded scene too:
		// otherwise the line returns to the quest's previous interaction while
		// the marker correctly points at the NPC the guide says to visit.
		return step.navigationInstruction(instruction);
	}

	private static WorldPoint nearestSeller(Step step, WorldPoint from)
	{
		WorldPoint best = null;
		int closest = Integer.MAX_VALUE;
		for (Step.Seller seller : step.getSellers())
		{
			for (List<Integer> raw : seller.getPoints())
			{
				if (raw == null || raw.size() < 3)
				{
					continue;
				}
				final WorldPoint at = new WorldPoint(raw.get(0), raw.get(1), raw.get(2));
				final int away = Math.abs(at.getX() - from.getX())
					+ Math.abs(at.getY() - from.getY());
				if (away < closest)
				{
					closest = away;
					best = at;
				}
			}
		}
		return best;
	}
}
