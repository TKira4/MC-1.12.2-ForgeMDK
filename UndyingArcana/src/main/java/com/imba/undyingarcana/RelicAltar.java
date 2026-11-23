package com.imba.undyingarcana;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/** Relic Altar: right-click it with a Relic Blueprint to forge the legendary weapon (handled by the pack scripts). */
public class RelicAltar extends Block {
    public RelicAltar() {
        super(Material.ROCK);
        setHardness(5.0F);
        setResistance(1200.0F);
        setLightLevel(0.6F);
        setHarvestLevel("pickaxe", 2);
    }
}
