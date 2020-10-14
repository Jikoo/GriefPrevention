/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.events.PreventPvPEvent;
import me.ryanhamshire.GriefPrevention.events.ProtectDeathDropsEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

//handles events related to entities
public class EntityEventHandler implements Listener
{
    //convenience reference for the singleton datastore
    private final DataStore dataStore;
    private final GriefPrevention instance;
    private final NamespacedKey luredByPlayer;

    public EntityEventHandler(DataStore dataStore, GriefPrevention plugin)
    {
        this.dataStore = dataStore;
        instance = plugin;
        luredByPlayer = new NamespacedKey(plugin, "lured_by_player");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityFormBlock(EntityBlockFormEvent event)
    {
        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.PLAYER)
        {
            Player player = (Player) event.getEntity();
            String noBuildReason = GriefPrevention.instance.allowBuild(player, event.getBlock().getLocation(), event.getNewState().getType());
            if (noBuildReason != null)
            {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onLightningStrike(LightningStrikeEvent event)
    {
        if (event.getCause() == LightningStrikeEvent.Cause.TRIDENT)
            event.getLightning().setMetadata("GP_TRIDENT", new FixedMetadataValue(GriefPrevention.instance, event.getLightning().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityChangeBLock(EntityChangeBlockEvent event)
    {
        if (!GriefPrevention.instance.config_endermenMoveBlocks && event.getEntityType() == EntityType.ENDERMAN)
        {
            event.setCancelled(true);
        }
        else if (!GriefPrevention.instance.config_silverfishBreakBlocks && event.getEntityType() == EntityType.SILVERFISH)
        {
            event.setCancelled(true);
        }
        else if (!GriefPrevention.instance.config_rabbitsEatCrops && event.getEntityType() == EntityType.RABBIT)
        {
            event.setCancelled(true);
        }
        else if (GriefPrevention.instance.config_claims_worldModes.get(event.getBlock().getWorld()) != ClaimsMode.Disabled)
        {
            if (event.getEntityType() == EntityType.WITHER)
            {
                Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);
                if (claim == null || !claim.areExplosivesAllowed || !GriefPrevention.instance.config_blockClaimExplosions)
                {
                    event.setCancelled(true);
                }
            }
            else if (!GriefPrevention.instance.config_claims_ravagersBreakBlocks && event.getEntityType() == EntityType.RAVAGER)
            {
                event.setCancelled(true);
            }

            //don't allow crops to be trampled, except by a player with build permission
            else if (event.getTo() == Material.DIRT && event.getBlock().getType() == Material.FARMLAND)
            {
                if (event.getEntityType() != EntityType.PLAYER)
                {
                    event.setCancelled(true);
                }
                else
                {
                    Player player = (Player) event.getEntity();
                    Block block = event.getBlock();
                    if (GriefPrevention.instance.allowBreak(player, block, block.getLocation()) != null)
                    {
                        event.setCancelled(true);
                    }
                }
            }
        }

        //Prevent breaking lilypads via collision with a boat. Thanks Jikoo.
        else if (event.getEntity() instanceof Vehicle && !event.getEntity().getPassengers().isEmpty())
        {
            Entity driver = event.getEntity().getPassengers().get(0);
            if (driver instanceof Player)
            {
                Block block = event.getBlock();
                if (GriefPrevention.instance.allowBreak((Player) driver, block, block.getLocation()) != null)
                {
                    event.setCancelled(true);
                }
            }
        }

        //sand cannon fix - when the falling block doesn't fall straight down, take additional anti-grief steps
        else if (event.getEntityType() == EntityType.FALLING_BLOCK)
        {
            FallingBlock entity = (FallingBlock) event.getEntity();
            Block block = event.getBlock();

            //if changing a block TO air, this is when the falling block formed.  note its original location
            if (event.getTo() == Material.AIR)
            {
                entity.setMetadata("GP_FALLINGBLOCK", new FixedMetadataValue(GriefPrevention.instance, block.getLocation()));
            }
            //otherwise, the falling block is forming a block.  compare new location to original source
            else
            {
                List<MetadataValue> values = entity.getMetadata("GP_FALLINGBLOCK");
                //if we're not sure where this entity came from (maybe another plugin didn't follow the standard?), allow the block to form
                //Or if entity fell through an end portal, allow it to form, as the event is erroneously fired twice in this scenario.
                if (values.size() < 1) return;

                Location originalLocation = (Location) (values.get(0).value());
                Location newLocation = block.getLocation();

                //if did not fall straight down
                if (originalLocation.getBlockX() != newLocation.getBlockX() || originalLocation.getBlockZ() != newLocation.getBlockZ())
                {
                    //in creative mode worlds, never form the block
                    if (GriefPrevention.instance.config_claims_worldModes.get(newLocation.getWorld()) == ClaimsMode.Creative)
                    {
                        event.setCancelled(true);
                        return;
                    }

                    //in other worlds, if landing in land claim, only allow if source was also in the land claim
                    Claim claim = this.dataStore.getClaimAt(newLocation, false, null);
                    if (claim != null && !claim.contains(originalLocation, false, false))
                    {
                        //when not allowed, drop as item instead of forming a block
                        event.setCancelled(true);

                        ItemStack itemStack = new ItemStack(entity.getMaterial(), 1);
                        Item item = block.getWorld().dropItem(entity.getLocation(), itemStack);
                        item.setVelocity(new Vector());
                    }
                }
            }
        }
    }

    //Used by "sand cannon" fix to ignore fallingblocks that fell through End Portals
    //This is largely due to a CB issue with the above event
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onFallingBlockEnterPortal(EntityPortalEnterEvent event)
    {
        if (event.getEntityType() != EntityType.FALLING_BLOCK)
            return;
        event.getEntity().removeMetadata("GP_FALLINGBLOCK", instance);
    }

    //Don't let people drop in TNT through end portals
    //Necessarily this shouldn't be an issue anyways since the platform is obsidian...
    @EventHandler(ignoreCancelled = true)
    void onTNTExitPortal(EntityPortalExitEvent event)
    {
        if (event.getEntityType() != EntityType.PRIMED_TNT)
            return;
        if (event.getTo().getWorld().getEnvironment() != Environment.THE_END)
            return;
        event.getEntity().remove();
    }

    //don't allow zombies to break down doors
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onZombieBreakDoor(EntityBreakDoorEvent event)
    {
        if (!GriefPrevention.instance.config_zombiesBreakDoors) event.setCancelled(true);
    }

    //don't allow entities to trample crops
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityInteract(EntityInteractEvent event)
    {
        Material material = event.getBlock().getType();
        if (material == Material.FARMLAND)
        {
            if (!GriefPrevention.instance.config_creaturesTrampleCrops)
            {
                event.setCancelled(true);
            }
            else
            {
                Entity rider = event.getEntity().getPassenger();
                if (rider != null && rider.getType() == EntityType.PLAYER)
                {
                    event.setCancelled(true);
                }
            }
        }
    }

    //when an entity explodes...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent explodeEvent)
    {
        this.handleExplosion(explodeEvent.getLocation(), explodeEvent.getEntity(), explodeEvent.blockList());
    }

    //when a block explodes...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent explodeEvent)
    {
        this.handleExplosion(explodeEvent.getBlock().getLocation(), null, explodeEvent.blockList());
    }


    void handleExplosion(Location location, Entity entity, List<Block> blocks)
    {
        //only applies to claims-enabled worlds
        World world = location.getWorld();

        if (!GriefPrevention.instance.claimsEnabledForWorld(world)) return;

        //FEATURE: explosions don't destroy surface blocks by default
        boolean isCreeper = (entity != null && entity.getType() == EntityType.CREEPER);

        boolean applySurfaceRules = world.getEnvironment() == Environment.NORMAL && ((isCreeper && GriefPrevention.instance.config_blockSurfaceCreeperExplosions) || (!isCreeper && GriefPrevention.instance.config_blockSurfaceOtherExplosions));

        //special rule for creative worlds: explosions don't destroy anything
        if (GriefPrevention.instance.creativeRulesApply(location))
        {
            for (int i = 0; i < blocks.size(); i++)
            {
                Block block = blocks.get(i);

                blocks.remove(i--);
            }

            return;
        }

        //make a list of blocks which were allowed to explode
        List<Block> explodedBlocks = new ArrayList<>();
        Claim cachedClaim = null;
        for (Block block : blocks)
        {
            //always ignore air blocks
            if (block.getType() == Material.AIR) continue;

            //is it in a land claim?
            Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, cachedClaim);
            if (claim != null)
            {
                cachedClaim = claim;
            }

            //if yes, apply claim exemptions if they should apply
            if (claim != null && (claim.areExplosivesAllowed || !GriefPrevention.instance.config_blockClaimExplosions))
            {
                explodedBlocks.add(block);
                continue;
            }

            //if claim is under siege, allow soft blocks to be destroyed
            if (claim != null && claim.siegeData != null)
            {
                Material material = block.getType();
                boolean breakable = GriefPrevention.instance.config_siege_blocks.contains(material);

                if (breakable) continue;
            }

            //if no, then also consider surface rules
            if (claim == null)
            {
                if (!applySurfaceRules || block.getLocation().getBlockY() < GriefPrevention.instance.getSeaLevel(world) - 7)
                {
                    explodedBlocks.add(block);
                }
            }
        }

        //clear original damage list and replace with allowed damage list
        blocks.clear();
        blocks.addAll(explodedBlocks);
    }

    //when an item spawns...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event)
    {
        //if in a creative world, cancel the event (don't drop items on the ground)
        if (GriefPrevention.instance.creativeRulesApply(event.getLocation()))
        {
            event.setCancelled(true);
        }

        //if item is on watch list, apply protection
        ArrayList<PendingItemProtection> watchList = GriefPrevention.instance.pendingItemWatchList;
        Item newItem = event.getEntity();
        Long now = null;
        for (int i = 0; i < watchList.size(); i++)
        {
            PendingItemProtection pendingProtection = watchList.get(i);
            //ignore and remove any expired pending protections
            if (now == null) now = System.currentTimeMillis();
            if (pendingProtection.expirationTimestamp < now)
            {
                watchList.remove(i--);
                continue;
            }
            //skip if item stack doesn't match
            if (pendingProtection.itemStack.getAmount() != newItem.getItemStack().getAmount() ||
                    pendingProtection.itemStack.getType() != newItem.getItemStack().getType())
            {
                continue;
            }

            //skip if new item location isn't near the expected spawn area
            Location spawn = event.getLocation();
            Location expected = pendingProtection.location;
            if (!spawn.getWorld().equals(expected.getWorld()) ||
                    spawn.getX() < expected.getX() - 5 ||
                    spawn.getX() > expected.getX() + 5 ||
                    spawn.getZ() < expected.getZ() - 5 ||
                    spawn.getZ() > expected.getZ() + 5 ||
                    spawn.getY() < expected.getY() - 15 ||
                    spawn.getY() > expected.getY() + 3)
            {
                continue;
            }

            //otherwise, mark item with protection information
            newItem.setMetadata("GP_ITEMOWNER", new FixedMetadataValue(GriefPrevention.instance, pendingProtection.owner));

            //and remove pending protection data
            watchList.remove(i);
            break;
        }
    }

    //when an experience bottle explodes...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onExpBottle(ExpBottleEvent event)
    {
        //if in a creative world, cancel the event (don't drop exp on the ground)
        if (GriefPrevention.instance.creativeRulesApply(event.getEntity().getLocation()))
        {
            event.setExperience(0);
        }
    }

    //when a creature spawns...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitySpawn(CreatureSpawnEvent event)
    {
        //these rules apply only to creative worlds
        if (!GriefPrevention.instance.creativeRulesApply(event.getLocation())) return;

        //chicken eggs and breeding could potentially make a mess in the wilderness, once griefers get involved
        SpawnReason reason = event.getSpawnReason();
        if (reason != SpawnReason.SPAWNER_EGG && reason != SpawnReason.BUILD_IRONGOLEM && reason != SpawnReason.BUILD_SNOWMAN && event.getEntityType() != EntityType.ARMOR_STAND)
        {
            event.setCancelled(true);
            return;
        }

        //otherwise, just apply the limit on total entities per claim (and no spawning in the wilderness!)
        Claim claim = this.dataStore.getClaimAt(event.getLocation(), false, null);
        if (claim == null || claim.allowMoreEntities(true) != null)
        {
            event.setCancelled(true);
            return;
        }
    }

    //when an entity dies...
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event)
    {
        LivingEntity entity = event.getEntity();

        //don't do the rest in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(entity.getWorld())) return;

        //special rule for creative worlds: killed entities don't drop items or experience orbs
        if (GriefPrevention.instance.creativeRulesApply(entity.getLocation()))
        {
            event.setDroppedExp(0);
            event.getDrops().clear();
        }

        //FEATURE: when a player is involved in a siege (attacker or defender role)
        //his death will end the siege

        if (entity.getType() != EntityType.PLAYER) return;  //only tracking players

        Player player = (Player) entity;
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        //if involved in a siege
        if (playerData.siegeData != null)
        {
            //end it, with the dieing player being the loser
            this.dataStore.endSiege(playerData.siegeData, null, player.getName(), event.getDrops());
        }

        //FEATURE: lock dropped items to player who dropped them

        World world = entity.getWorld();

        //decide whether or not to apply this feature to this situation (depends on the world where it happens)
        boolean isPvPWorld = GriefPrevention.instance.pvpRulesApply(world);
        if ((isPvPWorld && GriefPrevention.instance.config_lockDeathDropsInPvpWorlds) ||
                (!isPvPWorld && GriefPrevention.instance.config_lockDeathDropsInNonPvpWorlds))
        {
            Claim claim = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
            ProtectDeathDropsEvent protectionEvent = new ProtectDeathDropsEvent(claim);
            Bukkit.getPluginManager().callEvent(protectionEvent);
            if (!protectionEvent.isCancelled())
            {
                //remember information about these drops so that they can be marked when they spawn as items
                long expirationTime = System.currentTimeMillis() + 3000;  //now + 3 seconds
                Location deathLocation = player.getLocation();
                UUID playerID = player.getUniqueId();
                List<ItemStack> drops = event.getDrops();
                for (ItemStack stack : drops)
                {
                    GriefPrevention.instance.pendingItemWatchList.add(
                            new PendingItemProtection(deathLocation, playerID, expirationTime, stack));
                }

                //allow the player to receive a message about how to unlock any drops
                playerData.dropsAreUnlocked = false;
                playerData.receivedDropUnlockAdvertisement = false;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onItemMerge(ItemMergeEvent event)
    {
        Item item = event.getEntity();
        List<MetadataValue> data = item.getMetadata("GP_ITEMOWNER");
        event.setCancelled(data != null && data.size() > 0);
    }

    //when an entity picks up an item
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickup(EntityChangeBlockEvent event)
    {
        //FEATURE: endermen don't steal claimed blocks

        //if its an enderman
        if (event.getEntity().getType() == EntityType.ENDERMAN)
        {
            //and the block is claimed
            if (this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null) != null)
            {
                //he doesn't get to steal it
                event.setCancelled(true);
            }
        }
    }

    //when a painting is broken
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onHangingBreak(HangingBreakEvent event)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        //Ignore cases where itemframes should break due to no supporting blocks
        if (event.getCause() == RemoveCause.PHYSICS) return;

        //FEATURE: claimed paintings are protected from breakage

        //explosions don't destroy hangings
        if (event.getCause() == RemoveCause.EXPLOSION)
        {
            event.setCancelled(true);
            return;
        }

        //only allow players to break paintings, not anything else (like water and explosions)
        if (!(event instanceof HangingBreakByEntityEvent))
        {
            event.setCancelled(true);
            return;
        }

        HangingBreakByEntityEvent entityEvent = (HangingBreakByEntityEvent) event;

        //who is removing it?
        Entity remover = entityEvent.getRemover();

        //again, making sure the breaker is a player
        if (remover.getType() != EntityType.PLAYER)
        {
            event.setCancelled(true);
            return;
        }

        //if the player doesn't have build permission, don't allow the breakage
        Player playerRemover = (Player) entityEvent.getRemover();
        String noBuildReason = GriefPrevention.instance.allowBuild(playerRemover, event.getEntity().getLocation(), Material.AIR);
        if (noBuildReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(playerRemover, TextMode.Err, noBuildReason);
        }
    }

    //when a painting is placed...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPaintingPlace(HangingPlaceEvent event)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        //FEATURE: similar to above, placing a painting requires build permission in the claim

        //if the player doesn't have permission, don't allow the placement
        String noBuildReason = GriefPrevention.instance.allowBuild(event.getPlayer(), event.getEntity().getLocation(), Material.PAINTING);
        if (noBuildReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, noBuildReason);
            return;
        }

        //otherwise, apply entity-count limitations for creative worlds
        else if (GriefPrevention.instance.creativeRulesApply(event.getEntity().getLocation()))
        {
            PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer().getUniqueId());
            Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, playerData.lastClaim);
            if (claim == null) return;

            String noEntitiesReason = claim.allowMoreEntities(false);
            if (noEntitiesReason != null)
            {
                GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, noEntitiesReason);
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isMonster(Entity entity)
    {
        if (entity instanceof Monster) return true;

        EntityType type = entity.getType();
        if (type == EntityType.GHAST || type == EntityType.MAGMA_CUBE || type == EntityType.SHULKER)
            return true;

        if (type == EntityType.SLIME)
            return ((Slime) entity).getSize() > 0;

        if (type == EntityType.RABBIT)
            return ((Rabbit) entity).getRabbitType() == Rabbit.Type.THE_KILLER_BUNNY;

        if (type == EntityType.PANDA)
            return ((Panda) entity).getMainGene() == Panda.Gene.AGGRESSIVE;

        if (type == EntityType.HOGLIN || type == EntityType.POLAR_BEAR)
            return !entity.getPersistentDataContainer().has(luredByPlayer, PersistentDataType.BYTE) && ((Mob) entity).getTarget() != null;

        return false;
    }

    // Tag passive animals that can become aggressive so we can tell whether or not they are hostile later
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityTarget(EntityTargetEvent event)
    {
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        EntityType entityType = event.getEntityType();
        if (entityType != EntityType.HOGLIN && entityType != EntityType.POLAR_BEAR)
            return;

        if (event.getReason() == EntityTargetEvent.TargetReason.TEMPT)
            event.getEntity().getPersistentDataContainer().set(luredByPlayer, PersistentDataType.BYTE, (byte) 1);
        else
            event.getEntity().getPersistentDataContainer().remove(luredByPlayer);

    }

    //when an entity is damaged
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event)
    {
        this.handleEntityDamageEvent(event, true);
    }

    //when an entity is set on fire
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event)
    {
        //handle it just like we would an entity damge by entity event, except don't send player messages to avoid double messages
        //in cases like attacking with a flame sword or flame arrow, which would ALSO trigger the direct damage event handler

        EntityDamageByEntityEvent eventWrapper = new EntityDamageByEntityEvent(event.getCombuster(), event.getEntity(), DamageCause.FIRE_TICK, event.getDuration());
        this.handleEntityDamageEvent(eventWrapper, false);
        event.setCancelled(eventWrapper.isCancelled());
    }

    private void handleEntityDamageEvent(EntityDamageEvent event, boolean sendErrorMessagesToPlayers)
    {
        // Monsters are never protected.
        if (isMonster(event.getEntity())) return;

        // Certain entity types can be configured to be ignored.
        if (isIgnoredEntity(event)) return;

        // Protected death loot can't be destroyed, only picked up or despawned due to expiration.
        if (handleProtectedDrop(event)) return;

        // Protect pets from environmental damage types which could be easily caused by griefers.
        if (handlePetEnvironmentalDamage(event)) return;

        // All other handling specifically is for entities damaging other entities.
        if (!(event instanceof EntityDamageByEntityEvent)) return;

        EntityDamageByEntityEvent subEvent = (EntityDamageByEntityEvent) event;

        // TODO: review lightning handling - currently may not be usable in PvP at all
        if (subEvent.getDamager() instanceof LightningStrike && subEvent.getDamager().hasMetadata("GP_TRIDENT"))
        {
            event.setCancelled(true);
            return;
        }

        //determine which player is attacking, if any
        Player attacker = null;
        Projectile projectile = null;
        Entity damageSource = subEvent.getDamager();

        // Check if the damage source is a player.
        if (damageSource.getType() == EntityType.PLAYER)
        {
            attacker = (Player) damageSource;
        }
        // If not, check if the damage source is a projectile shot by a player.
        else if (damageSource instanceof Projectile)
        {
            projectile = (Projectile) damageSource;
            if (projectile.getShooter() instanceof Player)
            {
                attacker = (Player) projectile.getShooter();
            }
        }

        // Protect players from lingering potion damage when protected from PvP.
        if (damageSource.getType() == EntityType.AREA_EFFECT_CLOUD && isPvPProtected(event.getEntity()) != PvPProtectionState.NONE)
        {
            event.setCancelled(true);
            return;
        }

        // TODO: discuss this previously nonfunctional code - disables fireworks from crossbows in worlds not running PvP rules
        //if the attacker is a firework from a crossbow by a player and defender is a player (nonpvp)
        if (projectile != null && projectile.getType() == EntityType.FIREWORK
                && event.getEntityType() == EntityType.PLAYER && !GriefPrevention.instance.pvpRulesApply(projectile.getWorld()))
        {
            Player defender = (Player) (event.getEntity());
            if (attacker != defender)
            {
                event.setCancelled(true);
                return;
            }
        }

        // Protect qualifying players from PvP damage.
        if (handlePvP(subEvent, attacker, sendErrorMessagesToPlayers)) return;

        //don't track in worlds where claims are not enabled
        if (!this.instance.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        // Protect players from others' pets when protected from pvp.
        if (handlePetDamagerPvP(subEvent)) return;

        // Claimed entities used for display/container/trading purposes can only be hurt by a player with build trust.
        if (handleContainerEntity(subEvent, attacker, sendErrorMessagesToPlayers)) return;

        // Prevent players from attacking pets where necessary.
        if (handlePetDamagedPvP(subEvent, attacker, sendErrorMessagesToPlayers)) return;

        // Handle remaining cases of damage to creatures.
        if (handleCreatureDamage(subEvent, attacker, projectile, sendErrorMessagesToPlayers)) return;
    }

    // Helper method: check if a damaged entity is configured to not be handled by GriefPrevention.
    private boolean isIgnoredEntity(EntityDamageEvent event)
    {
        // Horse protections can be disabled in favor of other plugins' protections.
        if (!this.instance.config_claims_protectHorses && event.getEntity() instanceof Horse) return true;
        if (!this.instance.config_claims_protectDonkeys
                && (event.getEntity() instanceof Donkey || event.getEntity() instanceof Mule))
            return true;
        if (!this.instance.config_claims_protectLlamas && event.getEntity() instanceof Llama) return true;
        // Most creature protections can be disabled. Villagers and tamed animals follow special rules.
        if (!this.instance.config_claims_protectCreatures &&
                (event.getEntity() instanceof Creature && !(event.getEntity() instanceof Tameable || event.getEntity() instanceof Merchant)))
            return true;

        return false;
    }

    // Helper method: handle damage to dropped items protected by GriefPrevention.
    private boolean handleProtectedDrop(EntityDamageEvent event)
    {
        if (event.getEntityType() == EntityType.DROPPED_ITEM && event.getEntity().hasMetadata("GP_ITEMOWNER"))
        {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    // Helper method: handle environmental damage to tamed animals protected by GriefPrevention.
    private boolean handlePetEnvironmentalDamage(EntityDamageEvent event)
    {
        if (!(event.getEntity() instanceof Tameable) || this.instance.pvpRulesApply(event.getEntity().getWorld()))
            return false;

        Tameable tameable = (Tameable) event.getEntity();

        if (!tameable.isTamed()) return false;

        switch (event.getCause())
        {
            case ENTITY_EXPLOSION:
            case FALLING_BLOCK:
            case FIRE:
            case FIRE_TICK:
            case LAVA:
            case SUFFOCATION:
            case CONTACT:
            case DROWNING:
                event.setCancelled(true);
                return true;
            default:
                return false;
        }
    }

    // Helper method: check if an entity is a Player protected from PvP.
    private PvPProtectionState isPvPProtected(Entity entity)
    {
        // Ensure entity is a player.
        if (!(entity instanceof Player)) return PvPProtectionState.NONE;

        // Ensure PvP rules are enabled in world.
        if (!this.instance.pvpRulesApply(entity.getWorld())) return PvPProtectionState.NONE;

        Player player = (Player) entity;
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        // Recently spawned players are protected.
        if (this.instance.config_pvp_protectFreshSpawns && playerData.pvpImmune)
            return PvPProtectionState.FRESH_SPAWN;

        // If claims are not enabled, don't attempt to check for claim protection.
        if (!this.instance.claimsEnabledForWorld(player.getWorld())) return PvPProtectionState.NONE;

        Claim claim = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);

        // If player is not in a claim, the area is not a PvP safe zone.
        if (claim == null) return PvPProtectionState.NONE;

        playerData.lastClaim = claim;

        // If the claim is not a PvP safe zone, the player is not protected.
        if (!this.instance.claimIsPvPSafeZone(claim)) return PvPProtectionState.NONE;

        // Call PreventPvPEvent to allow addons to modify PvP.
        PreventPvPEvent pvpEvent = new PreventPvPEvent(claim);
        Bukkit.getPluginManager().callEvent(pvpEvent);

        // If the event is cancelled, the player is not protected.
        if (pvpEvent.isCancelled()) return PvPProtectionState.NONE;

        // Otherwise, the player is protected by a claim.
        return PvPProtectionState.CLAIM;
    }

    private enum PvPProtectionState
    {
        NONE, FRESH_SPAWN, CLAIM
    }

    // Helper method: handle direct PvP.
    private boolean handlePvP(EntityDamageByEntityEvent event, Player attacker, boolean sendErrors)
    {
        if (attacker == null || !(event.getEntity() instanceof Player)) return false;

        PvPProtectionState defenderState = isPvPProtected(event.getEntity());

        // Protect freshly spawned players.
        if (defenderState == PvPProtectionState.FRESH_SPAWN)
        {
            return cancelDamage(event, attacker, Messages.ThatPlayerPvPImmune, sendErrors);
        }

        boolean attackerIgnoringClaims = this.dataStore.getPlayerData(attacker.getUniqueId()).ignoreClaims;

        // Unless attacker is ignoring claims, respect claim protections.
        if (!attackerIgnoringClaims && defenderState == PvPProtectionState.CLAIM)
        {
            return cancelDamage(event, attacker, Messages.PlayerInPvPSafeZone, sendErrors);
        }

        PvPProtectionState attackerState = isPvPProtected(attacker);

        // Also prevent attack if defender cannot retaliate unless protection is from a claim and attacker is ignoring claims.
        if (!attackerIgnoringClaims && attackerState == PvPProtectionState.CLAIM
                || attackerState == PvPProtectionState.FRESH_SPAWN)
        {
            return cancelDamage(event, attacker, Messages.CantFightWhileImmune, sendErrors);
        }

        return true;
    }

    // Helper method: cancel damage and send error.
    private boolean cancelDamage(EntityDamageEvent event, Player attacker, Messages error, boolean sendErrors)
    {
        return cancelDamage(event, attacker, this.dataStore.getMessage(error), sendErrors);
    }

    // Helper method: cancel damage and send error.
    private boolean cancelDamage(EntityDamageEvent event, Player attacker, String error, boolean sendErrors)
    {
        event.setCancelled(true);
        if (sendErrors)
            GriefPrevention.sendMessage(attacker, TextMode.Err, error);
        return true;
    }

    // Helper method: handle owned pets attacking players.
    private boolean handlePetDamagerPvP(EntityDamageByEntityEvent event)
    {
        // Ensure damager is a pet.
        if (!(event.getDamager() instanceof Tameable)) return false;

        Tameable pet = (Tameable) event.getDamager();

        // Ensure pet is owned.
        if (!pet.isTamed() || pet.getOwner() == null) return false;

        PvPProtectionState defenderState = isPvPProtected(event.getEntity());

        // Ensure defender is protected from PvP.
        if (defenderState == PvPProtectionState.NONE) return false;

        event.setCancelled(true);
        pet.setTarget(null);
        return true;
    }

    // Helper method: handle damage of display/container/trading entities.
    private boolean handleContainerEntity(EntityDamageByEntityEvent event, Player attacker, boolean sendErrors)
    {
        // Claimed entities used for display/container/trading purposes can only be hurt by a player with build trust.
        if (event.getEntityType() != EntityType.ITEM_FRAME
                && event.getEntityType() != EntityType.ARMOR_STAND
                && event.getEntityType() != EntityType.ENDER_CRYSTAL
                && !(event.getEntity() instanceof Merchant))
            return false;

        Entity damager = event.getDamager();

        // Exception: Villagers are allowed to be damaged by raiders. // TODO several missed cases of raiders
        // From BigScary: Why exception? Admins can set up a village which can't be CHANGED by players, but must be "protected" by players.
        if (event.getEntity() instanceof Merchant
                && (damager instanceof Zombie
                || damager.getType() == EntityType.VINDICATOR
                || damager.getType() == EntityType.EVOKER
                || damager.getType() == EntityType.EVOKER_FANGS
                || damager.getType() == EntityType.VEX))
            return true;

        Claim cachedClaim = null;
        PlayerData playerData = null;

        if (attacker != null)
        {
            playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, cachedClaim);

        // If not claimed, fall through to next rule.
        if (claim == null) return false;

        // If attacker isn't a player, always cancel.
        if (attacker == null)
        {
            event.setCancelled(true);
            return true;
        }

        // Set attacker's last claim.
        playerData.lastClaim = claim;

        // Players must have build trust to damage structural entities.
        String failureReason = claim.allowBuild(attacker, Material.AIR);
        if (failureReason != null)
        {
            cancelDamage(event, attacker, failureReason, sendErrors);
        }

        return true;
    }

    // Helper method: handle tamed animals damaged by players.
    private boolean handlePetDamagedPvP(EntityDamageByEntityEvent event, Player attacker, boolean sendErrors)
    {
        // Attacker must be a player and defender must be a pet.
        if (attacker == null || !(event.getEntity() instanceof Tameable)) return false;

        Tameable tameable = (Tameable) event.getEntity();

        // Pet must have an owner set.
        if (!tameable.isTamed() || tameable.getOwner() == null) return false;

        UUID ownerID = tameable.getOwner().getUniqueId();

        // Always allow owners to damage their own pets.
        if (attacker.getUniqueId().equals(ownerID)) return true;

        // Allow admins ignoring claims to bypass protections.
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());
        if (attackerData.ignoreClaims) return true;

        // Disallow if attacker is pvp immune.
        if (attackerData.pvpImmune) return cancelDamage(event, attacker, Messages.CantFightWhileImmune, sendErrors);

        // Disallow in non-pvp worlds. In PvP worlds
        if (!this.instance.pvpRulesApply(tameable.getLocation().getWorld()) || (this.instance.config_pvp_protectPets && tameable.getType() != EntityType.WOLF))
        {
            String ownerName = GriefPrevention.lookupPlayerName(ownerID);
            String message = this.dataStore.getMessage(Messages.NoDamageClaimedEntity, ownerName);
            if (attacker.hasPermission("griefprevention.ignoreclaims"))
                message += "  " + this.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
            if (sendErrors)
                GriefPrevention.sendMessage(attacker, TextMode.Err, message);
            PreventPvPEvent pvpEvent = new PreventPvPEvent(new Claim(tameable.getLocation(), tameable.getLocation(), null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null));
            Bukkit.getPluginManager().callEvent(pvpEvent);
            if (!pvpEvent.isCancelled())
            {
                event.setCancelled(true);
            }
            return true;
        }

        // If tameable is not a wolf, fall through to creature handling.
        if (tameable.getType() != EntityType.WOLF) return false;

        // If player is under attack by the wolf, allow them to retaliate.
        if (tameable.getTarget() != null && tameable.getTarget().equals(attacker)) return true;

        // Otherwise, deny the damage.
        String ownerName = tameable.getOwner().getName();
        String message = this.dataStore.getMessage(Messages.NoDamageClaimedEntity, ownerName);
        if (attacker.hasPermission("griefprevention.ignoreclaims"))
            message += "  " + this.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
        return cancelDamage(event, attacker, message, sendErrors);
    }

    // Helper method: handle all generic creature damage.
    private boolean handleCreatureDamage(EntityDamageByEntityEvent event, Player attacker, Projectile projectile, boolean sendErrors)
    {
        if (!(event.getEntity() instanceof Creature)) return false;

        Entity damageSource = event.getDamager();

        // If attacker is not available and damage is not likely sourced from a malicious player, allow.
        if (attacker == null
                && damageSource.getType() != EntityType.CREEPER
                && damageSource.getType() != EntityType.WITHER
                && damageSource.getType() != EntityType.ENDER_CRYSTAL
                && damageSource.getType() != EntityType.AREA_EFFECT_CLOUD
                && damageSource.getType() != EntityType.WITCH
                && !(damageSource instanceof Projectile)
                && !(damageSource instanceof Explosive)
                && !(damageSource instanceof ExplosiveMinecart))
        {
            return false;
        }

        Claim cachedClaim = null;
        PlayerData playerData = null;

        if (attacker != null)
        {
            playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, cachedClaim);

        // If unclaimed, allow damage.
        if (claim == null) return false;

        // If damaged by anything other than a player, cancel the event.
        if (attacker == null)
        {
            event.setCancelled(true);
            if (projectile != null)
            {
                projectile.remove();
            }

            return true;
        }

        // Cache claim for later use.
        playerData.lastClaim = claim;

        // Otherwise the attacker must have permission in the claim.
        String noContainersReason = claim.allowContainers(attacker);

        if (noContainersReason == null) {
            return true;
        }

        // Handle projectiles used for attacks to prevent infinite bounces.
        if (projectile != null)
        {
            // Fireworks should not warn shooter so as to not spam - multiple entities may be affected.
            if (projectile.getType() == EntityType.FIREWORK)
            {
                event.setCancelled(true);
                return true;
            }
            // Tridents can be retrieved and are hard to obtain. Remove velocity and teleport beneath.
            if (projectile.getType() == EntityType.TRIDENT)
            {
                // TODO test this crap
                projectile.setVelocity(new Vector(0, 0, 0));
                projectile.teleport(event.getEntity().getLocation());
                projectile.setBounce(false);
            }
            // Remove all other projectiles to prevent infinite bounces and warnings.
            else
            {
                projectile.remove();
            }
        }

        String message = this.dataStore.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
        if (attacker.hasPermission("griefprevention.ignoreclaims"))
            message += "  " + this.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);

        return cancelDamage(event, attacker, message, sendErrors);
    }

    //when an entity is damaged
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamageMonitor(EntityDamageByEntityEvent event)
    {
        //FEATURE: prevent players who very recently participated in pvp combat from hiding inventory to protect it from looting
        //FEATURE: prevent players who are in pvp combat from logging out to avoid being defeated

        if (event.getEntity().getType() != EntityType.PLAYER) return;

        Player defender = (Player) event.getEntity();

        //Ignore "damage" from snowballs, eggs, etc. from triggering the PvP timer
        if (event.getDamage() == 0) return;

        //if not in a pvp rules world, do nothing
        if (!GriefPrevention.instance.pvpRulesApply(defender.getWorld())) return;

        //determine which player is attacking, if any
        Player attacker = null;
        Entity damageSource = event.getDamager();

        if (damageSource.getType() == EntityType.PLAYER)
        {
            attacker = (Player) damageSource;
        }
        else if (damageSource instanceof Projectile)
        {
            Projectile arrow = (Projectile) damageSource;
            if (arrow.getShooter() instanceof Player)
            {
                attacker = (Player) arrow.getShooter();
            }
        }

        //if attacker not a player, do nothing
        if (attacker == null || attacker.getUniqueId().equals(defender.getUniqueId())) return;

        PlayerData defenderData = this.dataStore.getPlayerData(defender.getUniqueId());
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());

        long now = Calendar.getInstance().getTimeInMillis();
        defenderData.lastPvpTimestamp = now;
        defenderData.lastPvpPlayer = attacker.getName();
        attackerData.lastPvpTimestamp = now;
        attackerData.lastPvpPlayer = defender.getName();
    }

    //when a vehicle is damaged
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onVehicleDamage(VehicleDamageEvent event)
    {
        //all of this is anti theft code
        if (!GriefPrevention.instance.config_claims_preventTheft) return;

        //input validation
        if (event.getVehicle() == null) return;

        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getVehicle().getWorld())) return;

        //determine which player is attacking, if any
        Player attacker = null;
        Entity damageSource = event.getAttacker();
        EntityType damageSourceType = null;

        //if damage source is null or a creeper, don't allow the damage when the vehicle is in a land claim
        if (damageSource != null)
        {
            damageSourceType = damageSource.getType();

            if (damageSource.getType() == EntityType.PLAYER)
            {
                attacker = (Player) damageSource;
            }
            else if (damageSource instanceof Projectile)
            {
                Projectile arrow = (Projectile) damageSource;
                if (arrow.getShooter() instanceof Player)
                {
                    attacker = (Player) arrow.getShooter();
                }
            }
        }

        //if not a player and not an explosion, always allow
        if (attacker == null && damageSourceType != EntityType.CREEPER && damageSourceType != EntityType.WITHER && damageSourceType != EntityType.PRIMED_TNT)
        {
            return;
        }

        //NOTE: vehicles can be pushed around.
        //so unless precautions are taken by the owner, a resourceful thief might find ways to steal anyway
        Claim cachedClaim = null;
        PlayerData playerData = null;

        if (attacker != null)
        {
            playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.getVehicle().getLocation(), false, cachedClaim);

        //if it's claimed
        if (claim != null)
        {
            //if damaged by anything other than a player, cancel the event
            if (attacker == null)
            {
                event.setCancelled(true);
            }

            //otherwise the player damaging the entity must have permission
            else
            {
                String noContainersReason = claim.allowContainers(attacker);
                if (noContainersReason != null)
                {
                    event.setCancelled(true);
                    String message = GriefPrevention.instance.dataStore.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
                    if (attacker.hasPermission("griefprevention.ignoreclaims"))
                        message += "  " + GriefPrevention.instance.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                    GriefPrevention.sendMessage(attacker, TextMode.Err, message);
                    event.setCancelled(true);
                }

                //cache claim for later
                if (playerData != null)
                {
                    playerData.lastClaim = claim;
                }
            }
        }
    }

    //when a splash potion effects one or more entities...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPotionSplash(PotionSplashEvent event)
    {
        ThrownPotion potion = event.getPotion();

        //ignore potions not thrown by players
        ProjectileSource projectileSource = potion.getShooter();
        if (projectileSource == null) return;
        Player thrower = null;
        if ((projectileSource instanceof Player))
            thrower = (Player) projectileSource;

        Collection<PotionEffect> effects = potion.getEffects();
        for (PotionEffect effect : effects)
        {
            PotionEffectType effectType = effect.getType();

            //restrict some potions on claimed animals (griefers could use this to kill or steal animals over fences) //RoboMWM: include villagers
            if (effectType.getName().equals("JUMP") || effectType.getName().equals("POISON"))
            {
                Claim cachedClaim = null;
                for (LivingEntity effected : event.getAffectedEntities())
                {
                    if (effected.getType() == EntityType.VILLAGER || effected instanceof Animals)
                    {
                        Claim claim = this.dataStore.getClaimAt(effected.getLocation(), false, cachedClaim);
                        if (claim != null)
                        {
                            cachedClaim = claim;
                            if (thrower == null || claim.allowContainers(thrower) != null)
                            {
                                event.setIntensity(effected, 0);
                                instance.sendMessage(thrower, TextMode.Err, Messages.NoDamageClaimedEntity, claim.getOwnerName());
                                return;
                            }
                        }
                    }
                }
            }

            //Otherwise, ignore potions not thrown by players
            if (thrower == null) return;

            //otherwise, no restrictions for positive effects
            if (positiveEffects.contains(effectType)) continue;

            for (LivingEntity effected : event.getAffectedEntities())
            {
                //always impact the thrower
                if (effected == thrower) continue;

                //always impact non players
                if (effected.getType() != EntityType.PLAYER) continue;

                    //otherwise if in no-pvp zone, stop effect
                    //FEATURE: prevent players from engaging in PvP combat inside land claims (when it's disabled)
                else if (GriefPrevention.instance.config_pvp_noCombatInPlayerLandClaims || GriefPrevention.instance.config_pvp_noCombatInAdminLandClaims)
                {
                    Player effectedPlayer = (Player) effected;
                    PlayerData defenderData = this.dataStore.getPlayerData(effectedPlayer.getUniqueId());
                    PlayerData attackerData = this.dataStore.getPlayerData(thrower.getUniqueId());
                    Claim attackerClaim = this.dataStore.getClaimAt(thrower.getLocation(), false, attackerData.lastClaim);
                    if (attackerClaim != null && GriefPrevention.instance.claimIsPvPSafeZone(attackerClaim))
                    {
                        attackerData.lastClaim = attackerClaim;
                        PreventPvPEvent pvpEvent = new PreventPvPEvent(attackerClaim);
                        Bukkit.getPluginManager().callEvent(pvpEvent);
                        if (!pvpEvent.isCancelled())
                        {
                            event.setIntensity(effected, 0);
                            GriefPrevention.sendMessage(thrower, TextMode.Err, Messages.CantFightWhileImmune);
                        }
                        continue;
                    }

                    Claim defenderClaim = this.dataStore.getClaimAt(effectedPlayer.getLocation(), false, defenderData.lastClaim);
                    if (defenderClaim != null && GriefPrevention.instance.claimIsPvPSafeZone(defenderClaim))
                    {
                        defenderData.lastClaim = defenderClaim;
                        PreventPvPEvent pvpEvent = new PreventPvPEvent(defenderClaim);
                        Bukkit.getPluginManager().callEvent(pvpEvent);
                        if (!pvpEvent.isCancelled())
                        {
                            event.setIntensity(effected, 0);
                            GriefPrevention.sendMessage(thrower, TextMode.Err, Messages.PlayerInPvPSafeZone);
                        }
                    }
                }
            }
        }
    }

    public static final HashSet<PotionEffectType> positiveEffects = new HashSet<>(Arrays.asList
            (
                    PotionEffectType.ABSORPTION,
                    PotionEffectType.DAMAGE_RESISTANCE,
                    PotionEffectType.FAST_DIGGING,
                    PotionEffectType.FIRE_RESISTANCE,
                    PotionEffectType.HEAL,
                    PotionEffectType.HEALTH_BOOST,
                    PotionEffectType.INCREASE_DAMAGE,
                    PotionEffectType.INVISIBILITY,
                    PotionEffectType.JUMP,
                    PotionEffectType.NIGHT_VISION,
                    PotionEffectType.REGENERATION,
                    PotionEffectType.SATURATION,
                    PotionEffectType.SPEED,
                    PotionEffectType.WATER_BREATHING
            ));
}
