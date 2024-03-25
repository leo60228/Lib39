package com.unascribed.lib39.phantom;

import com.unascribed.lib39.core.api.AutoMixin;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;

public class Lib39PhantomMixin extends AutoMixin {
    private static final boolean CONNECTOR = FabricLoader.getInstance().isModLoaded("connectormod");

    @Override
    protected boolean shouldMixinBeSkipped(String name, ClassNode node) {
        if (name.contains(".connectormod")) {
            if (name.contains(".present")) {
                return !CONNECTOR;
            }
            return CONNECTOR;
        }

        return super.shouldMixinBeSkipped(name, node);
    }
}
