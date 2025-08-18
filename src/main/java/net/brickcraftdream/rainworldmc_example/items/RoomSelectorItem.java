package net.brickcraftdream.rainworldmc_example.items;


import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RoomSelectorItem extends Item {
    public RoomSelectorItem(Item.Settings properties) {
        super(properties);
    }

    @Override
    public @NotNull ActionResult useOnBlock(@NotNull ItemUsageContext context) {
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean canMine(@NotNull BlockState state, @NotNull World level, @NotNull BlockPos pos, PlayerEntity player) {
        return false;
    }

    @Override
    public void appendTooltip(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, List<Text> tooltip, @NotNull TooltipType tooltipFlag) {
        tooltip.add(Text.translatable("itemTooltip.rainworld.room_selector_item.first_line.use").formatted(Formatting.GOLD)
                .append(" ")
                .append(Text.translatable("itemTooltip.rainworld.room_selector_item.first_line.left_click").formatted(Formatting.RED))
                .append(" ")
                .append(Text.translatable("itemTooltip.rainworld.room_selector_item.first_line.and").formatted(Formatting.GOLD))
                .append(" ")
                .append(Text.translatable("itemTooltip.rainworld.room_selector_item.first_line.right_click").formatted(Formatting.RED))
                .append(" ")
                .append(Text.translatable("itemTooltip.rainworld.room_selector_item.first_line.rest").formatted(Formatting.GOLD))
        );
        tooltip.add(Text.translatable("itemTooltip.rainworld.room_selector_item.second_line.press").formatted(Formatting.GOLD)
                .append(" ")
                .append(Text.translatable("itemTooltip.rainworld.room_selector_item.second_line.ctrl").formatted(Formatting.RED))
                .append(" ")
                .append(Text.translatable("itemTooltip.rainworld.room_selector_item.second_line.confirm").formatted(Formatting.GOLD))
        );
    }

}

