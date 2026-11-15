package io.github.bl4z3es.riseofempires.mc.block;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import io.github.bl4z3es.riseofempires.mc.world.Tent;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Charter (task M1-10). Laid on an empty lectern it founds a camp there: the lectern takes
 * the Charter as a written book, and a tent and campfire go up beside it.
 */
public class CharterItem extends Item {
    public CharterItem(Properties properties) {
        super(properties);
    }

    /** The plans the founder receives with their camp. */
    public static final List<String> STARTER_PLANS = List.of("farm", "stable", "bunkhouse");

    /** A written book's title holds at most this many characters. */
    private static final int MAX_TITLE = 32;

    private static String title(String campName) {
        String full = "Charter of " + campName;
        return full.length() <= MAX_TITLE ? full : "The Charter";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (!state.is(Blocks.LECTERN) || state.getValue(LecternBlock.HAS_BOOK)) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        RoeWorld world = RoeWorld.get(level.getServer());
        if (player == null || world == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();

        Settlement owner = Founding.settlementAt(world.state(), RoeWorld.place(level, pos));
        if (owner != null) {
            player.sendSystemMessage(Component.literal("This land already answers to " + owner.name() + ".")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return InteractionResult.FAIL;
        }
        String name = player.getName().getString();
        Event founded = Founding.found(world.sim(), player.getUUID().toString(), name, RoeWorld.place(level, pos));
        if (founded == null) return InteractionResult.FAIL;
        Settlement camp = Founding.settlementAt(world.state(), RoeWorld.place(level, pos));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title(camp.name())), name, 0,
                List.of(Filterable.passThrough(Component.literal("By this Charter, recovered from the ruins of the old empire, "
                                + name + " claims the land within " + camp.claimRadius() + " paces of this lectern and names it "
                                + camp.name() + ".")),
                        Filterable.passThrough(Component.literal("Let any who seek shelter come to its fire.\n\n"
                                + "Recorded as event #" + founded.id() + "."))),
                true));
        LecternBlock.tryPlaceBook(player, level, pos, state, book);
        context.getItemInHand().shrink(1);
        Tent.raise(level, pos, founded.id());
        // The starter kits (CONTEXT.md section 2): one plan each for a farm, a stable and a bunkhouse.
        for (String plan : STARTER_PLANS) {
            ItemStack stack = PlanItem.of(plan);
            if (!player.getInventory().add(stack)) net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), stack);
        }
        player.sendSystemMessage(Component.literal("You have founded " + camp.name() + ". Keep the fire lit: tonight, "
                + "others may see it.").withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
