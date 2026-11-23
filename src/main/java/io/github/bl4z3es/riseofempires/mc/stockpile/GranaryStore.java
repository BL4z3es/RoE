package io.github.bl4z3es.riseofempires.mc.stockpile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The real containers around a Granary Seal, counted and changed as one store (rule E12). */
public final class GranaryStore {
	/** PROPOSED: not in Balance yet. Horizontal reach of a Granary Seal, in blocks. */
	public static final int RADIUS = 6;
	/** PROPOSED: not in Balance yet. Vertical reach of a Granary Seal, in blocks. */
	public static final int HEIGHT = 3;

	private final List<Container> containers = new ArrayList<>();

	public GranaryStore(ServerLevel level, BlockPos seal) {
		for (BlockPos p : BlockPos.betweenClosed(seal.offset(-RADIUS, -HEIGHT, -RADIUS), seal.offset(RADIUS, HEIGHT, RADIUS))) {
			if (!level.isLoaded(p)) continue;
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof Container c && !containers.contains(c)) containers.add(c);
		}
	}

	public Map<String, Integer> count() {
		Map<String, Integer> out = new TreeMap<>();
		for (Container c : containers) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.isEmpty()) out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** Applies every change or none. Negative amounts remove, positive insert. Returns false if it did not fit. */
	public boolean apply(Map<String, Integer> changes) {
		List<List<ItemStack>> snapshot = new ArrayList<>();
		for (Container c : containers) {
			List<ItemStack> copy = new ArrayList<>();
			for (int i = 0; i < c.getContainerSize(); i++) copy.add(c.getItem(i).copy());
			snapshot.add(copy);
		}
		boolean ok = true;
		for (Map.Entry<String, Integer> e : changes.entrySet()) {
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(e.getKey())).orElse(null);
			if (item == null) { ok = false; break; }
			int n = e.getValue();
			if (n < 0 && remove(item, -n) != 0) { ok = false; break; }
			if (n > 0 && insert(item, n) != 0) { ok = false; break; }
		}
		if (!ok) {
			for (int c = 0; c < containers.size(); c++) {
				for (int i = 0; i < snapshot.get(c).size(); i++) containers.get(c).setItem(i, snapshot.get(c).get(i));
			}
		}
		for (Container c : containers) c.setChanged();
		return ok;
	}

	/** Returns how many could not be removed. */
	private int remove(Item item, int amount) {
		for (Container c : containers) {
			for (int i = 0; i < c.getContainerSize() && amount > 0; i++) {
				ItemStack s = c.getItem(i);
				if (s.is(item)) amount -= c.removeItem(i, Math.min(amount, s.getCount())).getCount();
			}
		}
		return amount;
	}

	/** Returns how many did not fit: first into matching stacks, then into empty slots. */
	private int insert(Item item, int amount) {
		ItemStack probe = new ItemStack(item);
		for (int pass = 0; pass < 2 && amount > 0; pass++) {
			for (Container c : containers) {
				for (int i = 0; i < c.getContainerSize() && amount > 0; i++) {
					ItemStack s = c.getItem(i);
					if (!c.canPlaceItem(i, probe)) continue;
					int max = c.getMaxStackSize(probe);
					if (pass == 0 && !s.isEmpty() && ItemStack.isSameItemSameComponents(s, probe)) {
						int add = Math.min(amount, max - s.getCount());
						if (add > 0) { s.grow(add); amount -= add; }
					} else if (pass == 1 && s.isEmpty()) {
						int add = Math.min(amount, max);
						c.setItem(i, new ItemStack(item, add));
						amount -= add;
					}
				}
			}
		}
		return amount;
	}
}
