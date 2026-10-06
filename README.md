# Mob In A Bucket

A Fabric mod for **Minecraft 26.3** that lets you scoop up (almost) *any* mob —
passive or hostile — into a single bucket and pour it back out wherever you like.
Inspired by [JustDoom/VillagerInABucket](https://github.com/JustDoom/VillagerInABucket),
but generalised to work on every creature instead of just villagers.

Unlike the original (which uses a per-entity mixin for villagers, wandering traders
and zombie villagers), this mod hooks the generic item/entity interaction methods, so
it works on anything that lives — cows, horses, creepers, villagers, axolotls, zombies,
you name it — with no mixins at all.

## What it does

- **Scoop:** **sneak + right-click** any non-player living entity with an empty **Mob Bucket**
  to capture it. The creature's *entire* state is saved — health, age, variant, custom name,
  tamed owner, inventory, everything — so you get the exact same mob back, not a fresh one.
- **Release:** **sneak + right-click** a block with a full bucket to drop the creature onto
  the block face you clicked. The bucket empties and is ready to reuse.
- The sneak requirement means a normal right-click still works as usual while you hold the
  bucket — trading with villagers, mounting horses, opening chests, and so on.
- The captured creature's name shows in the item tooltip.
- Found in the **Tools & Utilities** creative tab.

It captures any `LivingEntity` (every mob, plus armor stands). It does **not** catch
non-living entities such as boats, minecarts, item frames or dropped items.

## Requirements

| Component       | Version        |
|-----------------|----------------|
| Minecraft       | 26.3           |
| Fabric Loader   | ≥ 0.19.5       |
| Fabric API      | any (0.161.0+26.3 tested) |
| Java            | 25             |

## Building

The project uses the Gradle wrapper, so you don't need Gradle installed — only a Java 25 JDK.
On Gentoo you likely have it at `/opt/openjdk-bin-25`:

```bash
JAVA_HOME=/opt/openjdk-bin-25 ./gradlew build
```

The finished mod jar is written to `build/libs/mobbucket-1.0.0.jar`
(ignore the `-sources.jar`).

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5+ for Minecraft 26.3.
2. Drop **both** of these into your `.minecraft/mods/` folder (or a server's `mods/`):
   - `mobbucket-1.0.0.jar`
   - the matching [Fabric API](https://modrinth.com/mod/fabric-api) jar for 26.3.
3. Launch the game. The **Mob Bucket** appears in the Tools & Utilities creative tab,
   or give yourself one with `/give @s mobbucket:mob_bucket`.

## Testing it quickly

```bash
JAVA_HOME=/opt/openjdk-bin-25 ./gradlew runClient   # single-player client
JAVA_HOME=/opt/openjdk-bin-25 ./gradlew runServer   # dedicated server
```

## How it works

Everything lives in two small classes:

- **`MobBucketMod`** — registers the `mob_bucket` item and adds it to the creative tab.
- **`MobBucketItem`** — the behaviour:
  - `interactLivingEntity(...)` captures the entity. The entity is serialised with the
    26.3 `ValueOutput` API (`TagValueOutput.createWithContext(...)` → `entity.save(...)`)
    and the resulting NBT is stored in the stack's `CUSTOM_DATA` component under
    `CapturedEntity`. The entity is then `discard()`ed.
  - `useOn(...)` rebuilds the entity from that NBT
    (`EntityType.by(input).flatMap(type -> EntityType.create(type, input, level, BUCKET))`),
    snaps it to the clicked block face, adds it to the world, and clears the component.

No capture/release state is stored anywhere except on the item stack itself, so filled
buckets survive being dropped, put in chests, traded, etc.

## Notes & limitations

- It captures **any** non-player living entity, including hostile mobs and bosses that
  allow serialisation. If you'd rather restrict it to passive animals only, change the guard
  in `interactLivingEntity` from `entity instanceof Player` to
  `!(entity instanceof net.minecraft.world.entity.animal.Animal)`.
- Passengers/leashed entities are captured in the NBT but only the single ridden creature is
  re-spawned on release (vanilla single-entity behaviour).
- Water creatures released onto land will behave exactly as if you'd emptied a vanilla fish
  bucket there.
- A dispenser-based auto-release (like the original mod's dispenser behaviour) is not
  included, but would be a straightforward addition.

## License

MIT — see [LICENSE](LICENSE). The bucket texture is a placeholder; swap in your own art at
`src/main/resources/assets/mobbucket/textures/item/mob_bucket.png`.
