package com.damezame97.white_lily.recipebook;

public enum transfer_reason
{
    OK, WRONG_DEVICE, UNSUPPORTED, NOT_AUTHORIZED, UNREGISTERED, MISSING_MATERIALS, MISSING_TOOL,
    MISSING_CONTAINER, INVENTORY_FULL, ALLOCATION, MENU_INVALID, DATA_CHANGED, COOLDOWN;
    public String key(){return "screen.white_lily.recipe_browser.reason."+name().toLowerCase(java.util.Locale.ROOT);}
}
