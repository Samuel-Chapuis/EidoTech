package fr.thoridan.menu;

import fr.thoridan.Techutilities;
import fr.thoridan.menu.PrinterMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Techutilities.MODID);

    public static final RegistryObject<MenuType<PrinterMenu>> PRINTER_MENU = MENUS.register("printer_menu",
            () -> IForgeMenuType.create(PrinterMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
