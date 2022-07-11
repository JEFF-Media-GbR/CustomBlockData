/*
 * Copyright (c) 2022 Alexander Majka (mfnalex) / JEFF Media GbR
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * If you need help or have any suggestions, feel free to join my Discord and head to #programming-help:
 *
 * Discord: https://discord.jeff-media.com/
 *
 * If you find this library helpful or if you're using it one of your paid plugins, please consider leaving a donation
 * to support the further development of this project :)
 *
 * Donations: https://paypal.me/mfnalex
 */

package com.jeff_media.customblockdata;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents a {@link PersistentDataContainer} for a specific {@link Block}. Also provides some static utility methods
 * that can be used on every PersistentDataContainer.
 *
 * By default, and for backward compatibility reasons, data stored inside blocks is independent of the underlying block.
 * That means: if you store some data inside a dirt block, and that block is now pushed by a piston, then the information
 * will still reside in the old block's location. <b>You can of course also make CustomBockData automatically take care of those situations</b>,
 * so that CustomBlockData will always be updated on certain Bukkit Events like BlockBreakEvent, EntityExplodeEvent, etc.
 * For more information about this please see {@link #registerListener(Plugin)}.
 */
public class CustomBlockData implements PersistentDataContainer {

    private static final char[] DEFAULT_PACKAGE = new char[] { 'c', 'o', 'm', '.', 'j', 'e', 'f', 'f', '_', 'm', 'e', 'd', 'i', 'a', '.', 'c', 'u', 's', 't', 'o', 'm', 'b', 'l', 'o', 'c', 'k', 'd', 'a', 't', 'a'};

    static {
        checkRelocation();
    }

    private static void checkRelocation() {
        if (CustomBlockData.class.getPackage().getName().equals(new String(DEFAULT_PACKAGE))) {
            JavaPlugin plugin = JavaPlugin.getProvidingPlugin(CustomBlockData.class);
            plugin.getLogger().warning("Nag author(s) " + String.join(", ", plugin.getDescription().getAuthors()) + " of plugin " + plugin.getName() + " for not relocating the CustomBlockData package.");
        }
    }

    private static final Set<Map.Entry<UUID, BlockVector>> DIRTY_BLOCKS = new HashSet<>();

    private static final PersistentDataType<?, ?>[] PRIMITIVE_DATA_TYPES = new PersistentDataType<?, ?>[]{
            PersistentDataType.BYTE,
            PersistentDataType.SHORT,
            PersistentDataType.INTEGER,
            PersistentDataType.LONG,
            PersistentDataType.FLOAT,
            PersistentDataType.DOUBLE,
            PersistentDataType.STRING,
            PersistentDataType.BYTE_ARRAY,
            PersistentDataType.INTEGER_ARRAY,
            PersistentDataType.LONG_ARRAY,
            PersistentDataType.TAG_CONTAINER_ARRAY,
            PersistentDataType.TAG_CONTAINER};
    private static final NamespacedKey PERSISTENCE_KEY = Objects.requireNonNull(NamespacedKey.fromString("customblockdata:protected"),"Could not create persistence NamespacedKey");
    private static final Pattern KEY_REGEX = Pattern.compile("^x(\\d+)y(-?\\d+)z(\\d+)$");
    private static final int CHUNK_MIN_XZ = 0;
    private static final int CHUNK_MAX_XZ = 15;
    private static final boolean HAS_MIN_HEIGHT_METHOD;

    static {
        boolean classExists = false;
        try {
            Class.forName("org.bukkit.generator.WorldInfo");
            classExists = true;
        } catch (final ClassNotFoundException ignored) {
        }
        HAS_MIN_HEIGHT_METHOD = classExists;
    }

    private final PersistentDataContainer pdc;
    private final Chunk chunk;
    private final NamespacedKey key;

    private final Map.Entry<UUID,BlockVector> blockEntry;

    private final Plugin plugin;

    /**
     * Gets the PersistentDataContainer associated with the given block and plugin
     *
     * @param block  Block
     * @param plugin Plugin
     */
    public CustomBlockData(final @NotNull Block block, final @NotNull Plugin plugin) {
        this.chunk = block.getChunk();
        this.key = getKey(plugin, block);
        this.pdc = getPersistentDataContainer();
        this.blockEntry = getBlockEntry(block);
        this.plugin = plugin;
    }

    private static Map.Entry<UUID, BlockVector> getBlockEntry(final @NotNull Block block) {
        final UUID uuid = block.getWorld().getUID();
        final BlockVector blockVector = new BlockVector(block.getX(), block.getY(), block.getZ());
        return new AbstractMap.SimpleEntry<>(uuid, blockVector);
    }

    /**
     * Gets the Block associated with this CustomBlockData, or null if the world is no longer loaded.
     */
    public @Nullable Block getBlock() {
        World world = Bukkit.getWorld(blockEntry.getKey());
        if(world == null) return null;
        BlockVector vector = blockEntry.getValue();
        return world.getBlockAt(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    static boolean isDirty(Block block) {
        return DIRTY_BLOCKS.contains(getBlockEntry(block));
    }

    static void setDirty(Plugin plugin, Map.Entry<UUID,BlockVector> blockEntry) {
            DIRTY_BLOCKS.add(blockEntry);
            Bukkit.getScheduler().runTask(plugin, () -> DIRTY_BLOCKS.remove(blockEntry));
    }

    /**
     * Gets the PersistentDataContainer associated with this block.
     *
     * @return PersistentDataContainer of this block
     */
    @NotNull
    private PersistentDataContainer getPersistentDataContainer() {
        final PersistentDataContainer chunkPDC = chunk.getPersistentDataContainer();
        final PersistentDataContainer blockPDC;
        if (chunkPDC.has(key, PersistentDataType.TAG_CONTAINER)) {
            blockPDC = chunkPDC.get(key, PersistentDataType.TAG_CONTAINER);
            assert blockPDC != null;
            return blockPDC;
        }
        blockPDC = chunkPDC.getAdapterContext().newPersistentDataContainer();
        return blockPDC;
    }

    /**
     * Gets the PersistentDataContainer associated with the given block and plugin
     *
     * @param block     Block
     * @param namespace Namespace
     * @deprecated Use {@link #CustomBlockData(Block, Plugin)} instead.
     */
    @Deprecated
    public CustomBlockData(final @NotNull Block block, final @NotNull String namespace) {
        this.chunk = block.getChunk();
        this.key = new NamespacedKey(namespace, getKey(block));
        this.pdc = getPersistentDataContainer();
        this.plugin = JavaPlugin.getProvidingPlugin(CustomBlockData.class);
        this.blockEntry = getBlockEntry(block);
    }

    private static NamespacedKey getKey(Plugin plugin, Block block) {
        return new NamespacedKey(plugin, getKey(block));
    }

    /**
     * Gets whether this CustomBlockData is protected. Protected CustomBlockData will not be changed by any Bukkit Events
     * @see #registerListener(Plugin)
     */
    public boolean isProtected() {
        return has(PERSISTENCE_KEY, DataType.BOOLEAN);
    }

    /**
     * Sets whether this CustomBlockData is protected. Protected CustomBlockData will not be changed by any Bukkit Events
     * @see #registerListener(Plugin)
     */
    public void setProtected(boolean isProtected) {
        if(isProtected) {
            set(PERSISTENCE_KEY, DataType.BOOLEAN, true);
        } else {
            remove(PERSISTENCE_KEY);
        }
    }

    /**
     * Gets a String-based {@link NamespacedKey} that consists of the block's relative coordinates within its chunk
     *
     * @param block block
     * @return NamespacedKey consisting of the block's relative coordinates within its chunk
     */
    @NotNull
    static String getKey(@NotNull final Block block) {
        final int x = block.getX() & 0x000F;
        final int y = block.getY();
        final int z = block.getZ() & 0x000F;
        return "x" + x + "y" + y + "z" + z;
        //return String.format("x%dy%dz%d", x, y, z);
    }

    /**
     * Gets the block represented by the given {@link NamespacedKey} in the given {@link Chunk}
     */
    @Nullable
    static Block getBlockFromKey(final NamespacedKey key, final Chunk chunk) {
        final Matcher matcher = KEY_REGEX.matcher(key.getKey());
        if (!matcher.matches()) return null;
        final int x = Integer.parseInt(matcher.group(1));
        final int y = Integer.parseInt(matcher.group(2));
        final int z = Integer.parseInt(matcher.group(3));
        if ((x < CHUNK_MIN_XZ || x > CHUNK_MAX_XZ) || (z < CHUNK_MIN_XZ || z > CHUNK_MAX_XZ) || (y < getWorldMinHeight(chunk.getWorld()) || y > chunk.getWorld().getMaxHeight() - 1))
            return null;
        return chunk.getBlock(x, y, z);
    }

    /**
     * Returns the given {@link World}'s minimum build height, or 0 if not supported in this Bukkit version
     */
    static int getWorldMinHeight(final World world) {
        if (HAS_MIN_HEIGHT_METHOD) {
            return world.getMinHeight();
        } else {
            return 0;
        }
    }

    /**
     * Get if the given Block has any CustomBockData associated with it
     */
    public static boolean hasCustomBlockData(Block block, Plugin plugin) {
        return block.getChunk().getPersistentDataContainer().has(getKey(plugin, block), PersistentDataType.TAG_CONTAINER);
    }

    /**
     * Get if the given Block's CustomBlockData is protected. Protected CustomBlockData will not be changed by any Bukkit Events
     * @see #registerListener(Plugin)
     * @return true if the Block's CustomBlockData is protected, false if it doesn't have any CustomBlockData or it's not protected
     */
    public static boolean isProtected(Block block, Plugin plugin) {
        return new CustomBlockData(block,plugin).isProtected();
    }

    /**
     * Starts to listen and manage block-related events such as {@link BlockBreakEvent}. By default, CustomBlockData
     * is "stateless". That means: when you add data to a block, and now a player breaks the block, the data will
     * still reside at the original block location. This is to ensure that you always have full control about what data
     * is saved at which location.
     * <p>
     * If you do not want to handle this yourself, you can instead let CustomBlockData handle those events by calling this
     * method once. It will then listen to the common events itself, and automatically remove/update CustomBlockData.
     * <p>
     * Block changes made using the Bukkit API (e.g. {@link Block#setType(Material)}) or using a plugin like WorldEdit
     * will <b>not</b> be registered by this (but pull requests are welcome, of course)
     * <p>
     * For example, when you call this method in onEnable, CustomBlockData will now get automatically removed from a block
     * when a player breaks this block. It will additionally call custom events like {@link com.jeff_media.customblockdata.events.CustomBlockDataRemoveEvent}.
     * Those events implement {@link org.bukkit.event.Cancellable}. If one of the CustomBlockData events is cancelled,
     * it will not alter any CustomBlockData.
     *
     * @param plugin Your plugin's instance
     */
    public static void registerListener(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new BlockDataListener(plugin), plugin);
    }

    /**
     * Returns a Set of all blocks in this chunk containing Custom Block Data created by the given plugin
     *
     * @param plugin Plugin
     * @param chunk  Chunk
     * @return A Set containing all blocks in this chunk containing Custom Block Data created by the given plugin
     */
    @NotNull
    public static Set<Block> getBlocksWithCustomData(final Plugin plugin, final Chunk chunk) {
        final NamespacedKey dummy = new NamespacedKey(plugin, "dummy");
        return getBlocksWithCustomData(chunk, dummy);
    }

    /**
     * Returns a {@link Set} of all blocks in this {@link Chunk} containing Custom Block Data matching the given {@link NamespacedKey}'s namespace
     *
     * @param namespace Namespace
     * @param chunk     Chunk
     * @return A {@link Set} containing all blocks in this chunk containing Custom Block Data created by the given plugin
     */
    @NotNull
    private static Set<Block> getBlocksWithCustomData(final @NotNull Chunk chunk, final @NotNull NamespacedKey namespace) {
        final PersistentDataContainer chunkPDC = chunk.getPersistentDataContainer();
        return chunkPDC.getKeys().stream().filter(key -> key.getNamespace().equals(namespace.getNamespace())).map(key -> getBlockFromKey(key, chunk)).filter(Objects::nonNull).collect(Collectors.toSet());
    }


    /**
     * Returns a {@link Set} of all blocks in this {@link Chunk} containing Custom Block Data created by the given plugin
     *
     * @param namespace Namespace
     * @param chunk     Chunk
     * @return A {@link Set} containing all blocks in this chunk containing Custom Block Data created by the given plugin
     */
    @NotNull
    public static Set<Block> getBlocksWithCustomData(final String namespace, final Chunk chunk) {
        @SuppressWarnings("deprecation") final NamespacedKey dummy = new NamespacedKey(namespace, "dummy");
        return getBlocksWithCustomData(chunk, dummy);
    }

    /**
     * Removes all CustomBlockData and disables the protection status ({@link #setProtected(boolean)}
     */
    public void clear() {
        pdc.getKeys().forEach(pdc::remove);
        save();
    }

    /**
     * Saves the block's {@link PersistentDataContainer} inside the chunk's PersistentDataContainer
     */
    private void save() {
        setDirty(plugin,blockEntry);
        if (pdc.isEmpty()) {
            chunk.getPersistentDataContainer().remove(key);
        } else {
            chunk.getPersistentDataContainer().set(key, PersistentDataType.TAG_CONTAINER, pdc);
        }
    }

    /**
     * Copies all data to another block. Data already present in the destination block will keep intact, unless it gets
     * overwritten by identically named keys. Data in the source block won't be changed.
     */
    @SuppressWarnings({"unchecked", "rawtypes", "ConstantConditions"})
    public void copyTo(Block block, Plugin plugin) {
        CustomBlockData newCbd = new CustomBlockData(block, plugin);
        getKeys().forEach(key -> {
            PersistentDataType dataType = getDataType(this, key);
            if (dataType == null) return;
            newCbd.set(key, dataType, get(key, dataType));
        });
    }

    /**
     * Gets the proper primitive {@link PersistentDataType} for the given {@link NamespacedKey} in the given {@link PersistentDataContainer}
     *
     * @return The primitive PersistentDataType for the given key, or null if the key doesn't exist
     */
    public static PersistentDataType<?, ?> getDataType(PersistentDataContainer pdc, NamespacedKey key) {
        for (PersistentDataType<?, ?> dataType : PRIMITIVE_DATA_TYPES) {
            if (pdc.has(key, dataType)) return dataType;
        }
        return null;
    }

    @Override
    public <T, Z> void set(final @NotNull NamespacedKey namespacedKey, final @NotNull PersistentDataType<T, Z> persistentDataType, final @NotNull Z z) {
        pdc.set(namespacedKey, persistentDataType, z);
        save();
    }

    @Override
    public <T, Z> boolean has(final @NotNull NamespacedKey namespacedKey, final @NotNull PersistentDataType<T, Z> persistentDataType) {
        return pdc.has(namespacedKey, persistentDataType);
    }
    
    @Override
    public (T,Z) boolean has(final @NotNull NamespacedKey namespacedKey) {
        return pdc.has(namespacedKey);
    }

    @Nullable
    @Override
    public <T, Z> Z get(final @NotNull NamespacedKey namespacedKey, final @NotNull PersistentDataType<T, Z> persistentDataType) {
        return pdc.get(namespacedKey, persistentDataType);
    }

    @NotNull
    @Override
    public <T, Z> Z getOrDefault(final @NotNull NamespacedKey namespacedKey, final @NotNull PersistentDataType<T, Z> persistentDataType, final @NotNull Z z) {
        return pdc.getOrDefault(namespacedKey, persistentDataType, z);
    }

    @NotNull
    @Override
    public Set<NamespacedKey> getKeys() {
        return pdc.getKeys();
    }

    @Override
    public void remove(final @NotNull NamespacedKey namespacedKey) {
        pdc.remove(namespacedKey);
        save();
    }

    @Override
    public boolean isEmpty() {
        return pdc.isEmpty();
    }

    @NotNull
    @Override
    public PersistentDataAdapterContext getAdapterContext() {
        return pdc.getAdapterContext();
    }

    /**
     * Gets the proper primitive {@link PersistentDataType} for the given {@link NamespacedKey}
     *
     * @return The primitive PersistentDataType for the given key, or null if the key doesn't exist
     */
    public PersistentDataType<?, ?> getDataType(NamespacedKey key) {
        return getDataType(this, key);
    }

    private static final class DataType {
        private static final PersistentDataType<Byte,Boolean> BOOLEAN = new PersistentDataType<Byte, Boolean>() {
            @NotNull
            @Override
            public Class<Byte> getPrimitiveType() {
                return Byte.class;
            }

            @NotNull
            @Override
            public Class<Boolean> getComplexType() {
                return Boolean.class;
            }

            @NotNull
            @Override
            public Byte toPrimitive(@NotNull Boolean complex, @NotNull PersistentDataAdapterContext context) {
                return complex ? (byte) 1 : (byte) 0;
            }

            @NotNull
            @Override
            public Boolean fromPrimitive(@NotNull Byte primitive, @NotNull PersistentDataAdapterContext context) {
                return primitive == (byte) 1;
            }
        };
    }
}

