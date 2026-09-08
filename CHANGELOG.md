# 1.1.0 — Bug fixes

## Following quests

- Quest steps now highlight what Quest Helper highlights: the NPC to talk to,
  the object to search, the raft to board, the rope to use.
- Quest interfaces are guided too, so screens like the Dwarf Cannon repair
  show you which part to click.
- "Start <quest>" steps tick when you start the quest, instead of waiting for
  something much later in it — and they still tick if you had already started.
- Steps that run "until you have X" now stop on the item itself, so the guide
  hands back exactly where it says it does.
- Training, gathering and diary steps no longer tick themselves because an
  unrelated quest happened to move.

## Getting there

- Better routing through closed doors, gates and fences, with the way in
  highlighted rather than a path that stops at a wall.

## Around the interface

- Bank withdrawals, shop stock and ground items are highlighted again.
- Shopkeepers and quest NPCs are marked, not just named in the step text.

## Under the hood

- The plugin repeats far less work each frame and each tick, which should help
  on lower-end machines.
