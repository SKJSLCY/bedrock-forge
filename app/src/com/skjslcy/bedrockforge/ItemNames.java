package com.skjslcy.bedrockforge;

/** 物品 ID → 中文名（使用内置翻译表）。 */
final class ItemNames {

    private ItemNames() {
    }

    /** 把 minecraft:xxx 转成中文名，查不到时返回清理后的英文。 */
    static String display(String rawId) {
        if (rawId == null || rawId.isEmpty()) return "";
        String id = rawId;
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        String cn = Translations.item(id);
        if (cn != null && !cn.isEmpty()) return cn;
        return id.replace('_', ' ');
    }

    /** 按盔甲列表顺序给出部位名。 */
    static String armorSlotByIndex(int i) {
        switch (i) {
            case 0: return "头盔";
            case 1: return "胸甲";
            case 2: return "护腿";
            case 3: return "靴子";
            default: return "装备 " + i;
        }
    }
}
