package com.mraof.minestuck.entity.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mraof.minestuck.advancements.MSCriteriaTriggers;
import com.mraof.minestuck.entity.consort.ConsortEntity;
import com.mraof.minestuck.entity.consort.ConsortReputation;
import com.mraof.minestuck.entity.consort.ConsortRewardHandler;
import com.mraof.minestuck.entity.dialogue.condition.Condition;
import com.mraof.minestuck.inventory.ConsortMerchantInventory;
import com.mraof.minestuck.player.Echeladder;
import com.mraof.minestuck.player.PlayerBoondollars;
import com.mraof.minestuck.player.PlayerData;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A Trigger allows for new code to be called when a dialogue option is picked
 */
@MethodsReturnNonnullByDefault
public sealed interface Trigger
{
	Codec<Trigger> CODEC = Triggers.REGISTRY.byNameCodec().dispatch(Trigger::codec, Function.identity());
	Codec<List<Trigger>> LIST_CODEC = Trigger.CODEC.listOf();
	
	MapCodec<? extends Trigger> codec();
	
	void triggerEffect(LivingEntity entity, ServerPlayer player);
	
	record SetDialogue(ResourceLocation newPath) implements Trigger
	{
		static final MapCodec<SetDialogue> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("new_path").forGetter(SetDialogue::newPath)
		).apply(instance, SetDialogue::new));
		
		@Override
		public MapCodec<SetDialogue> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(entity instanceof DialogueEntity dialogueEntity)
				dialogueEntity.getDialogueComponent().setDialogue(this.newPath, false);
		}
	}
	
	record SetDialogueFromList(List<ResourceLocation> newPaths) implements Trigger
	{
		static final MapCodec<SetDialogueFromList> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.list(ResourceLocation.CODEC).fieldOf("new_paths").forGetter(SetDialogueFromList::newPaths)
		).apply(instance, SetDialogueFromList::new));
		
		@Override
		public MapCodec<SetDialogueFromList> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(entity instanceof DialogueEntity dialogueEntity)
			{
				dialogueEntity.getDialogueComponent().setDialogue(Util.getRandom(this.newPaths, entity.level().random), false);
			}
		}
	}
	
	record SetPlayerDialogue(ResourceLocation dialogueId) implements Trigger
	{
		static final MapCodec<SetPlayerDialogue> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("dialogue").forGetter(SetPlayerDialogue::dialogueId)
		).apply(instance, SetPlayerDialogue::new));
		
		@Override
		public MapCodec<SetPlayerDialogue> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			((DialogueEntity) entity).getDialogueComponent().setDialogueForPlayer(player, this.dialogueId);
		}
	}
	
	record OpenConsortMerchantGui(ResourceKey<LootTable> lootTable) implements Trigger
	{
		static final MapCodec<OpenConsortMerchantGui> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(OpenConsortMerchantGui::lootTable)
		).apply(instance, OpenConsortMerchantGui::new));
		
		@Override
		public MapCodec<OpenConsortMerchantGui> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(entity instanceof ConsortEntity consortEntity)
			{
				if(consortEntity.stocks == null)
				{
					consortEntity.stocks = new ConsortMerchantInventory(consortEntity, ConsortRewardHandler.generateStock(this.lootTable, consortEntity, consortEntity.level().random));
				}
				
				player.openMenu(new SimpleMenuProvider(consortEntity, Component.literal("Consort shop")), consortEntity::writeShopMenuBuffer);
			}
		}
	}
	
	record Command(String commandText) implements Trigger
	{
		static final MapCodec<Command> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.fieldOf("command").forGetter(Command::commandText)
		).apply(instance, Command::new));
		
		@Override
		public MapCodec<Command> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(player == null)
				return;
			
			//TODO using the entity for this instead of the player failed
			CommandSourceStack sourceStack = player.createCommandSourceStack().withSuppressedOutput().withPermission(Commands.LEVEL_GAMEMASTERS);
			player.server.getCommands().performPrefixedCommand(sourceStack, this.commandText);
		}
	}
	
	record TakeItem(Item item, int amount) implements Trigger
	{
		static final MapCodec<TakeItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(TakeItem::item),
				Codec.INT.optionalFieldOf("amount", 1).forGetter(TakeItem::amount)
		).apply(instance, TakeItem::new));
		
		public TakeItem(Item item)
		{
			this(item, 1);
		}
		
		@Override
		public MapCodec<TakeItem> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(player == null)
				return;
			
			ItemStack stack = Condition.PlayerHasItem.findPlayerItem(this.item, player, this.amount);
			if(stack != null)
				stack.shrink(this.amount);
		}
	}
	
	/**
	 * Take one of the item that was matched by a {@link com.mraof.minestuck.entity.dialogue.condition.Condition.ItemTagMatch}.
	 */
	enum TakeMatchedItem implements Trigger
	{
		INSTANCE;
		static final MapCodec<TakeMatchedItem> CODEC = MapCodec.unit(INSTANCE);
		
		@Override
		public MapCodec<TakeMatchedItem> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			DialogueComponent component = ((DialogueEntity) entity).getDialogueComponent();
			Optional<Item> matchedItem = component.getMatchedItem(player);
			matchedItem.ifPresent(item -> {
				ItemStack matchedStack = Condition.PlayerHasItem.findPlayerItem(item, player, 1);
				if(matchedStack != null)
					matchedStack.shrink(1);
			});
		}
	}
	
	static DataResult<EquipmentSlot> parseSlot(String slotName)
	{
		try
		{
			return DataResult.success(EquipmentSlot.byName(slotName));
		} catch(IllegalArgumentException e)
		{
			return DataResult.error(() -> "Not a valid name for an EquipmentSlot: " + slotName);
		}
	}
	
	Codec<EquipmentSlot> EQUIPMENT_CODEC = Codec.STRING.comapFlatMap(
			Trigger::parseSlot,
			EquipmentSlot::getName
	);
	
	record SetNPCItem(Item item, EquipmentSlot slot) implements Trigger
	{
		static final MapCodec<SetNPCItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(SetNPCItem::item),
				EQUIPMENT_CODEC.optionalFieldOf("slot", EquipmentSlot.MAINHAND).forGetter(SetNPCItem::slot)
		).apply(instance, SetNPCItem::new));
		
		@Override
		public MapCodec<SetNPCItem> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			entity.setItemSlot(slot, new ItemStack(item));
		}
	}
	
	/**
	 * If a matched item is found in the player inventory, it is removed from there and then added to the specified slot in the npc inventory
	 */
	record SetNPCMatchedItem(EquipmentSlot slot) implements Trigger
	{
		static final MapCodec<SetNPCMatchedItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				EQUIPMENT_CODEC.optionalFieldOf("slot", EquipmentSlot.MAINHAND).forGetter(SetNPCMatchedItem::slot)
		).apply(instance, SetNPCMatchedItem::new));
		
		@Override
		public MapCodec<SetNPCMatchedItem> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			DialogueComponent component = ((DialogueEntity) entity).getDialogueComponent();
			Optional<Item> matchedItem = component.getMatchedItem(player);
			matchedItem.ifPresent(item -> {
				ItemStack matchedStack = Condition.PlayerHasItem.findPlayerItem(item, player, 1);
				if(matchedStack != null)
				{
					entity.setItemSlot(slot, new ItemStack(item));
					matchedStack.shrink(1);
				}
			});
		}
	}
	
	record GiveItem(Item item, int amount) implements Trigger
	{
		static final MapCodec<GiveItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(GiveItem::item),
				Codec.INT.optionalFieldOf("amount", 1).forGetter(GiveItem::amount)
		).apply(instance, GiveItem::new));
		
		public GiveItem(Item item)
		{
			this(item, 1);
		}
		
		@Override
		public MapCodec<GiveItem> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(player == null)
				return;
			
			player.addItem(new ItemStack(item, amount));
		}
	}
	
	record GiveFromLootTable(ResourceKey<LootTable> lootTable) implements Trigger
	{
		private static final Logger LOGGER = LogManager.getLogger();
		static final MapCodec<GiveFromLootTable> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(GiveFromLootTable::lootTable)
		).apply(instance, GiveFromLootTable::new));
		
		@Override
		public MapCodec<GiveFromLootTable> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(player == null)
				return;
			
			LootParams.Builder builder = new LootParams.Builder((ServerLevel) entity.level())
					.withParameter(LootContextParams.THIS_ENTITY, entity).withParameter(LootContextParams.ORIGIN, entity.position());
			List<ItemStack> loot = entity.getServer().reloadableRegistries().getLootTable(lootTable)
					.getRandomItems(builder.create(LootContextParamSets.GIFT));
			
			if(loot.isEmpty())
				LOGGER.warn("Tried to generate loot from {}, but no items were generated!", lootTable);
			
			for(ItemStack itemstack : loot)
			{
				player.spawnAtLocation(itemstack, 0.0F);
				if(entity instanceof ConsortEntity consortEntity)
					MSCriteriaTriggers.CONSORT_ITEM.get().trigger(player, lootTable.toString(), itemstack, consortEntity);
			}
		}
	}
	
	record AddConsortReputation(int reputation) implements Trigger
	{
		static final MapCodec<AddConsortReputation> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.INT.fieldOf("reputation").forGetter(AddConsortReputation::reputation)
		).apply(instance, AddConsortReputation::new));
		
		@Override
		public MapCodec<AddConsortReputation> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(!(entity instanceof ConsortEntity consortEntity))
				return;
			
			PlayerData.get(player).ifPresent(playerData ->
					ConsortReputation.get(playerData).addConsortReputation(this.reputation, consortEntity.getHomeDimension())
			);
		}
	}
	
	record AddBoondollars(int boondollars) implements Trigger
	{
		static final MapCodec<AddBoondollars> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.INT.fieldOf("boondollars").forGetter(AddBoondollars::boondollars)
		).apply(instance, AddBoondollars::new));
		
		@Override
		public MapCodec<AddBoondollars> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			Optional<PlayerData> data = PlayerData.get(player);
			if(data.isPresent() && boondollars != 0)
			{
				if(boondollars > 0)
					PlayerBoondollars.addBoondollars(data.get(), boondollars);
				else
					PlayerBoondollars.takeBoondollars(data.get(), -boondollars);
			}
		}
	}
	
	record AddEcheladderExperience(int xp) implements Trigger
	{
		static final MapCodec<AddEcheladderExperience> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.INT.fieldOf("xp").forGetter(AddEcheladderExperience::xp)
		).apply(instance, AddEcheladderExperience::new));
		
		@Override
		public MapCodec<AddEcheladderExperience> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(player == null)
				return;
			
			Echeladder.get(player).increaseProgress(xp);
		}
	}
	
	record Explode() implements Trigger
	{
		static final MapCodec<Explode> CODEC = MapCodec.unit(Explode::new);
		
		@Override
		public MapCodec<Explode> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(entity instanceof ConsortEntity consortEntity)
				consortEntity.setExplosionTimer();
		}
	}
	
	record SetFlag(String flag, boolean isPlayerSpecific) implements Trigger
	{
		static final MapCodec<SetFlag> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.fieldOf("flag").forGetter(SetFlag::flag),
				Codec.BOOL.fieldOf("player_specific").forGetter(SetFlag::isPlayerSpecific)
		).apply(instance, SetFlag::new));
		
		@Override
		public MapCodec<SetFlag> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(entity instanceof DialogueEntity dialogueEntity)
			{
				DialogueComponent component = dialogueEntity.getDialogueComponent();
				Set<String> flags = this.isPlayerSpecific ? component.playerFlags(player) : component.flags();
				flags.add(this.flag);
			}
		}
	}
	
	record SetRandomFlag(List<String> flags, boolean isPlayerSpecific) implements Trigger
	{
		static final MapCodec<SetRandomFlag> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.listOf().fieldOf("flags").forGetter(SetRandomFlag::flags),
				Codec.BOOL.fieldOf("player_specific").forGetter(SetRandomFlag::isPlayerSpecific)
		).apply(instance, SetRandomFlag::new));
		
		@Override
		public MapCodec<SetRandomFlag> codec()
		{
			return CODEC;
		}
		
		@Override
		public void triggerEffect(LivingEntity entity, ServerPlayer player)
		{
			if(this.flags.isEmpty())
				return;
			
			if(entity instanceof DialogueEntity dialogueEntity)
			{
				DialogueComponent component = dialogueEntity.getDialogueComponent();
				Set<String> flags = this.isPlayerSpecific ? component.playerFlags(player) : component.flags();
				if(this.flags.stream().noneMatch(flags::contains))
					flags.add(Util.getRandom(this.flags, entity.getRandom()));
			}
		}
	}
}
