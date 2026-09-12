package top.csituka.youzaiworldcore.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** 有界的页面层级；返回已有页面时裁掉子层，同类页面替换，避免返回路径形成循环。 */
public final class YzuiLayerHistory<T> {
    private static final int MAX_LAYERS = 8;
    private final List<T> layers = new ArrayList<>();

    public void clear() { layers.clear(); }

    public void change(T previous, T next, boolean overlay, BiPredicate<T, T> samePage) {
        if (next == null) { clear(); return; }
        if (previous == next) return;
        int existing = indexOf(next);
        if (existing < 0) {
            for (int i = layers.size() - 1; i >= 0; i--) {
                if (samePage.test(layers.get(i), next)) { existing = i; break; }
            }
        }
        if (existing >= 0) {
            layers.subList(existing, layers.size()).clear();
        } else if (!overlay || previous == null) {
            clear();
        } else if (layers.isEmpty() || layers.getLast() != previous) {
            clear();
            layers.add(previous);
        }
        layers.add(next);
        if (layers.size() > MAX_LAYERS) layers.removeFirst();
    }

    public T parent(T screen) {
        int index = indexOf(screen);
        return index > 0 ? layers.get(index - 1) : null;
    }

    public List<T> parents(T screen) {
        int index = indexOf(screen);
        return index > 0 ? List.copyOf(layers.subList(0, index)) : List.of();
    }

    private int indexOf(T screen) {
        for (int i = 0; i < layers.size(); i++) if (layers.get(i) == screen) return i;
        return -1;
    }
}
