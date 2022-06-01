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

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AllTests {

    ServerMock serverMock;
    MockPlugin plugin;
    World world;
    NamespacedKey dummy;
    int[][] coords;
    int[][] chunkCoords;

    @BeforeAll
    void setup() {
        serverMock = MockBukkit.mock();
        plugin = MockBukkit.load(MockPlugin.class);
        world = new WorldMock(Material.STONE, 64);
        dummy = new NamespacedKey(plugin,"key");
        coords = new int[][]{
                {0, 0, 0},
                {1, 64, 0},
                {2, 127, 0},
                {100, 127, 100},
                {1234, 64, 987654}
        };
        chunkCoords = new int[][]{
                {0,0,3}, // Chunk 0,0 must contain 3 custom PDC blocks - 0,0,0, 1,64,0 and 2,127,0
                {6,6,1}, // Chunk 6,6 must contain 1 custom PDC block - 100,127,100
                {77,61728,1} // Chunk 77,61728 must contain 1 custom PDC block - 1234, 64, 987654
        };
    }

    @AfterAll
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @Order(1)
    void addBlockData() {
        int currentValue = 0;
        for(final int[] coord : coords) {
            final Block block = world.getBlockAt(coord[0],coord[1],coord[2]);
            final CustomBlockData cbd = new CustomBlockData(block,plugin);
            assert cbd.getKeys().isEmpty();

            cbd.set(dummy, PersistentDataType.INTEGER,currentValue);

            final CustomBlockData cbd2 = new CustomBlockData(block, plugin);
            assert cbd2.get(dummy,PersistentDataType.INTEGER) == currentValue;

            //System.out.println("Set PDC value in chunk " + block.getChunk().getX() + ", " + block.getChunk().getZ());

            currentValue++;
        }
    }

    @Test
    @Order(2)
    void getBlockData() {
        for(final int[] chunkCoord : chunkCoords) {
            //System.out.println("Checking PDC values in chunk " + chunkCoord[0] + ", " + chunkCoord[1] + " (expecting " + chunkCoord[2] + ")");
            final Chunk chunk = world.getChunkAt(chunkCoord[0],chunkCoord[1]);
            assert CustomBlockData.getBlocksWithCustomData(plugin, chunk).size() == chunkCoord[2];
        }
    }

    @Test
    @Order(3)
    void removeBlockData() {
        for(int[] coord : coords) {
            CustomBlockData cbd = new CustomBlockData(world.getBlockAt(coord[0],coord[1],coord[2]),plugin);
            assert cbd.getKeys().size()==1;
            cbd.remove(dummy);
            assert cbd.getKeys().size()==0;
        }
    }

}
