package dev.storm.npcaddon.item;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "npcaddon");
    public static final RegistryObject<Item> WAND = ITEMS.register("command_wand",
        () -> new WandItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WAR_HORN = ITEMS.register("war_horn",
        () -> new WarHornItem(new Item.Properties().stacksTo(16)));

    private ModItems() {}

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::tab);
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(WAND);
            e.accept(WAR_HORN);
        }
    }
}
