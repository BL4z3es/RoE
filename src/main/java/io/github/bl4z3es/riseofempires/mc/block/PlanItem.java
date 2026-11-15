package io.github.bl4z3es.riseofempires.mc.block;

import io.github.bl4z3es.riseofempires.core.Wire;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.camp.Blueprint;
import io.github.bl4z3es.riseofempires.core.sim.camp.BuildSites;
import io.github.bl4z3es.riseofempires.core.sim.camp.ItemCount;
import io.github.bl4z3es.riseofempires.mc.world.Blueprints;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import io.github.bl4z3es.riseofempires.mc.world.Sites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A builder's plan for one blueprint (task M1-13). Used on the ground inside a claim it stakes
 * out a build site there, centred on the block clicked; the camp's builders then put it up from
 * the granary. The blueprint id is kept in the item's custom data as {@code blueprint}.
 */
public class PlanItem extends Item {
    public static final String KEY = "blueprint";

    public PlanItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(String blueprint) {
        ItemStack stack = new ItemStack(ModItems.PLAN);
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, blueprint);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        String name = blueprint.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + blueprint.substring(1).replace('_', ' ');
        stack.set(DataComponents.ITEM_NAME, Component.literal("Plan: " + name));
        return stack;
    }

    public static String blueprintOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(KEY, "");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        RoeWorld w = RoeWorld.get(level.getServer());
        if (player == null || w == null) return InteractionResult.PASS;
        Blueprint bp = Blueprints.get(blueprintOf(context.getItemInHand()));
        if (bp == null) {
            player.sendSystemMessage(Component.literal("The plan is smudged past reading.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        BlockPos clicked = context.getClickedPos();
        BlockPos origin = clicked.offset(-bp.sizeX() / 2, 0, -bp.sizeZ() / 2);
        Settlement s = Founding.settlementAt(w.state(), RoeWorld.place(level, clicked));
        if (s == null) {
            player.sendSystemMessage(Component.literal("Nobody here would build it: this land answers to no camp of yours.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return InteractionResult.FAIL;
        }
        String siteId = bp.id() + "-" + (w.sim().ledger().size() + 1);
        Event opened = bp.open(w.sim(), s.id(), siteId, 0);
        if (opened == null) return InteractionResult.FAIL;
        Place place = RoeWorld.place(level, origin);
        w.sim().emit(EventDraft.of(Sites.STAKED).at(place).actor(Ref.settlement(s.id())).actor(BuildSites.ref(siteId))
                .causedBy(opened)
                .effect(Effects.setTag(BuildSites.ref(siteId), Sites.ORIGIN, place.encode()))
                .summary(player.getName().getString() + " staked out the " + bp.name() + " at " + origin.getX() + " "
                        + origin.getY() + " " + origin.getZ() + "."));
        context.getItemInHand().shrink(1);
        player.sendSystemMessage(Component.literal("The " + bp.name() + " is staked out. Builders will need "
                + ItemCount.describe(bp.totalCost()) + " in the granary.").withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
