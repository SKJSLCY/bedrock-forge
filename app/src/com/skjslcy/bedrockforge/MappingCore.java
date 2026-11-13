package com.skjslcy.bedrockforge;

import java.util.HashSet;
import java.util.Set;

/** 纯逻辑：从“进服前后账号集合”里判断唯一新增项。 */
final class MappingCore {

    private MappingCore() {
    }

    static String pickSingle(Set<String> base, Set<String> now) {
        Set<String> added = new HashSet<>(now);
        added.removeAll(base);
        return added.size() == 1 ? added.iterator().next() : null;
    }
}
