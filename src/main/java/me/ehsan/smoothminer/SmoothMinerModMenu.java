package me.ehsan.smoothminer;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Registers the settings screen with Mod Menu. Only loaded when Mod Menu is installed. */
public final class SmoothMinerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SmoothMinerConfigScreen::new;
    }
}
