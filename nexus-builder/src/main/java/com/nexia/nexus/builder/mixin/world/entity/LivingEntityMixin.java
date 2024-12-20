package com.nexia.nexus.builder.mixin.world.entity;

import com.nexia.nexus.api.event.entity.LivingEntityDamageEvent;
import com.nexia.nexus.api.event.entity.LivingEntityDeathEvent;
import com.nexia.nexus.api.event.player.PlayerChangeMovementStateEvent;
import com.nexia.nexus.api.world.damage.DamageData;
import com.nexia.nexus.builder.extension.world.entity.EntityExtension;
import com.nexia.nexus.builder.extension.world.entity.LivingEntityExtension;
import com.nexia.nexus.builder.implementation.Wrapped;
import com.nexia.nexus.builder.implementation.world.damage.WrappedDamageData;
import com.nexia.nexus.builder.implementation.world.entity.WrappedLivingEntity;
import com.nexia.nexus.builder.implementation.world.entity.player.WrappedPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements LivingEntityExtension {
    @Shadow protected boolean dead;

    @Shadow protected abstract void dropEquipment();

    @Shadow protected abstract void dropExperience();

    @Shadow public abstract boolean isFallFlying();

    @Shadow public abstract double getAttributeValue(Attribute attribute);

    @Shadow public abstract ItemStack getBlockingItem();

    @Shadow public abstract ItemStack getMainHandItem();

    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    //BEGIN: LivingEntityDamageEvent
    @Unique LivingEntityDamageEvent damageEvent;
    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z", shift = At.Shift.BEFORE), cancellable = true)
    public void injectLivingEntityDamageEvent(DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        com.nexia.nexus.api.world.entity.LivingEntity entity = Wrapped.wrap(this, WrappedLivingEntity.class);
        DamageData data = Wrapped.wrap(damageSource, WrappedDamageData.class);
        this.damageEvent = new LivingEntityDamageEvent(entity, data, f);
        LivingEntityDamageEvent.BACKEND.invoke(this.damageEvent);
        if (this.damageEvent.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z", shift = At.Shift.BEFORE), argsOnly = true)
    public float changeDamage(float prev) {
        if (this.damageEvent != null) {
            return damageEvent.getDamage();
        }
        return prev;
    }

    @ModifyVariable(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z", shift = At.Shift.BEFORE), argsOnly = true)
    public DamageSource changeCause(DamageSource prev) {
        if (this.damageEvent != null) {
            return ((WrappedDamageData) damageEvent.getCause()).unwrap();
        }
        return prev;
    }

    @Inject(method = "hurt", at = @At("TAIL"))
    public void nullifyDamageEvent(DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        LivingEntityDamageEvent.BACKEND.invokeEndFunctions(this.damageEvent);
        this.damageEvent = null;
    }
    //END: LivingEntityDamageEvent

    //BEGIN: LivingEntityDeathEvent
    @Unique LivingEntityDeathEvent deathEvent;
    @Inject(method = "die", at = @At("HEAD"))
    public void injectLivingEntityDeathEvent(DamageSource damageSource, CallbackInfo ci) {
        if (!this.removed && !this.dead && this.deathEvent == null) {
            com.nexia.nexus.api.world.entity.LivingEntity entity = Wrapped.wrap(this, WrappedLivingEntity.class);
            DamageData data = Wrapped.wrap(damageSource, WrappedDamageData.class);
            boolean mobLoot = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
            this.deathEvent = new LivingEntityDeathEvent(entity, data, mobLoot, this.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY), mobLoot);
            LivingEntityDeathEvent.BACKEND.invoke(this.deathEvent);
        }
    }

    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    public boolean changeShouldDropItems(GameRules gameRules, GameRules.Key<GameRules.BooleanValue> key) {
        if (key.equals(GameRules.RULE_DOMOBLOOT) && this.deathEvent != null) {
            return this.deathEvent.isDropLoot();
        } else {
            return gameRules.getBoolean(key);
        }
    }

    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment()V"))
    public void disableShouldDropEquipment(LivingEntity livingEntity) {
        if (this.deathEvent == null || this.deathEvent.isDropEquipment()) {
            this.dropEquipment();
        }
    }

    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropExperience()V"))
    public void disableShouldDropExperience(LivingEntity livingEntity) {
        if (this.deathEvent == null || this.deathEvent.isDropExperience()) {
            this.dropExperience();
        }
    }

    @Redirect(method = "dropExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    public boolean changeShouldDropExperience(GameRules gameRules, GameRules.Key<GameRules.BooleanValue> key) {
        if (key.equals(GameRules.RULE_DOMOBLOOT) && deathEvent != null) {
            return deathEvent.isDropExperience();
        } else {
            return gameRules.getBoolean(key);
        }
    }

    @Override
    public boolean willDropItems() {
        return this.deathEvent == null || this.deathEvent.isDropLoot();
    }

    @Inject(method = "die", at = @At("TAIL"))
    public void nullifyEvent(DamageSource damageSource, CallbackInfo ci) {
        LivingEntityDeathEvent.BACKEND.invokeEndFunctions(this.deathEvent);
        this.deathEvent = null;
    }

    @Override
    public LivingEntityDeathEvent getDeathEvent() {
        return deathEvent;
    }

    @Override
    public void setDeathEvent(LivingEntityDeathEvent deathEvent) {
        this.deathEvent = deathEvent;
    }
    //END: LivingEntityDeathEvent

    @Unique private PlayerChangeMovementStateEvent changeMovementStateEvent;
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setSprinting", at = @At("HEAD"))
    public void injectChangeMovementStateEvent(boolean bl, CallbackInfo ci) {
        if ((Entity) this instanceof ServerPlayer && ((EntityExtension) this).injectChangeMovementStateEvent() && this.isSprinting() != bl) {
            ServerPlayer player = (ServerPlayer) (Object) this;
            this.changeMovementStateEvent = new PlayerChangeMovementStateEvent(Wrapped.wrap(player, WrappedPlayer.class), PlayerChangeMovementStateEvent.ChangedState.SPRINTING, bl);
            PlayerChangeMovementStateEvent.BACKEND.invoke(changeMovementStateEvent);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @ModifyVariable(method = "setSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setSprinting(Z)V", shift = At.Shift.BEFORE), argsOnly = true)
    public boolean modifyIsSprinting(boolean prev) {
        if (changeMovementStateEvent != null && (Entity) this instanceof ServerPlayer && ((EntityExtension) this).injectChangeMovementStateEvent()) {
            return changeMovementStateEvent.isCancelled() ? changeMovementStateEvent.getPreviousValue() : changeMovementStateEvent.getChangedValue();

        } else {
            return prev;
        }
    }

    @Inject(method = "setSprinting", at = @At("RETURN"))
    public void nullifyMovementStateEvent(boolean bl, CallbackInfo ci) {
        if (changeMovementStateEvent != null && ((EntityExtension) this).injectChangeMovementStateEvent()) {
            PlayerChangeMovementStateEvent.BACKEND.invokeEndFunctions(changeMovementStateEvent);
            changeMovementStateEvent = null;
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Redirect(method = {"travel", "updateFallFlying"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setSharedFlag(IZ)V"))
    public void injectChangeMovementStateEvent(LivingEntity livingEntity, int i, boolean bl) {
        if (i == 7 && (Entity) this instanceof ServerPlayer && ((EntityExtension) this).injectChangeMovementStateEvent() && this.isFallFlying() != bl) {
            this.changeMovementStateEvent = new PlayerChangeMovementStateEvent(Wrapped.wrap(this, WrappedPlayer.class), PlayerChangeMovementStateEvent.ChangedState.FALL_FLYING, bl);
            PlayerChangeMovementStateEvent.BACKEND.invoke(this.changeMovementStateEvent);

            this.setSharedFlag(i, changeMovementStateEvent.isCancelled() ? changeMovementStateEvent.getPreviousValue() : changeMovementStateEvent.getChangedValue());

            PlayerChangeMovementStateEvent.BACKEND.invokeEndFunctions(changeMovementStateEvent);
            changeMovementStateEvent = null;
        } else {
            this.setSharedFlag(i, bl);
        }
    }

    /**
     * @author NotCoded
     * @reason Fix Shield Knockback
     */
    @Overwrite
    public void knockback(float f, double d, double e) {
        LivingEntity instance = (LivingEntity) (Object) this;
        double g = this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        ItemStack itemStack = this.getBlockingItem();
        if (!itemStack.isEmpty()) {
            if (instance instanceof Player) g = Math.min(1.0, 1-(1-g)*(1-(double) ShieldItem.getShieldKnockbackResistanceValue(itemStack)));
            else g = Math.min(1.0, g + (double) ShieldItem.getShieldKnockbackResistanceValue(itemStack));
        }

        f = (float)((double)f * (1.0 - g));
        if (!(f <= 0.0F)) {
            instance.hasImpulse = true;
            Vec3 vec3 = instance.getDeltaMovement();
            Vec3 vec32 = (new Vec3(d, 0.0, e)).normalize().scale(f);
            instance.setDeltaMovement(vec3.x / 2.0 - vec32.x, instance.isOnGround() ? Math.min(0.4, (double)f * 0.75) : Math.min(0.4, vec3.y + (double)f * 0.5),vec3.z / 2.0 - vec32.z);
        }
    }

    @Inject(method = "blockedByShield", at = @At("TAIL"))
    private void playShieldBlockSound(LivingEntity livingEntity, CallbackInfo ci) {
        if (!(this.getMainHandItem().getItem() instanceof AxeItem)) {
            this.level.playSound(null, new BlockPos(this.position()), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F + this.level.random.nextFloat() * 0.4F);
        }
    }

    // https://github.com/Blumbo/LessAnnoyingFire/blob/1.19.4/src/main/java/net/blumbo/lessannoyingfire/mixin/LivingEntityMixin.java
    // Less Annoying Fire

    @Shadow private DamageSource lastDamageSource;

    @Shadow protected abstract void markHurt();

    @Unique
    DamageSource damageSource;

    @Inject(method = "hurt", at = @At(value = "FIELD", ordinal = 0, opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I"))
    private void setSource1(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        damageSource = source;
    }

    @Redirect(method = "hurt", at = @At(value = "FIELD", ordinal = 0, opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I"))
    private int getInvulnerabilityTicks(LivingEntity instance) {
        // Make fire caused invulnerability ticks irrelevant if damage comes from an entity
        if (fireDamageSource(lastDamageSource) && damageSource.getDirectEntity() != null) return 0;
        return invulnerableTime;
    }

    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;markHurt()V"))
    private void setSource2(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        damageSource = source;
    }

    @Redirect(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;markHurt()V"))
    private void velocityUpdateCondition(LivingEntity instance) {
        // Prevent fire from messing up movement
        if (!fireDamageSource(damageSource)) markHurt();
    }

    @Unique
    private static boolean fireDamageSource(DamageSource damageSource) {
        return damageSource == DamageSource.ON_FIRE || damageSource == DamageSource.IN_FIRE;
    }
}
