package com.nexia.nexus.builder.implementation.world.effect;

import com.nexia.nexus.api.world.effect.StatusEffect;
import com.nexia.nexus.api.world.effect.StatusEffectInstance;
import com.nexia.nexus.builder.exception.WrappingException;
import com.nexia.nexus.builder.extension.world.effect.MobEffectExtension;
import com.nexia.nexus.builder.implementation.Wrapped;
import com.nexia.nexus.builder.implementation.util.ObjectMappings;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public class WrappedStatusEffectInstance extends Wrapped<MobEffectInstance> implements StatusEffectInstance {
    private final StatusEffect effect;

    public WrappedStatusEffectInstance(MobEffectInstance wrapped) {
        super(wrapped);
        this.effect = convert(wrapped.getEffect());
    }

    @Override
    public StatusEffect getStatusEffect() {
        return effect;
    }

    @Override
    public int getTicksLeft() {
        return wrapped.getDuration();
    }

    @Override
    public int getAmplifier() {
        return wrapped.getAmplifier();
    }

    @Override
    public boolean isAmbient() {
        return wrapped.isAmbient();
    }

    @Override
    public StatusEffectInstance copy() {
        return StatusEffectInstance.create(this.getStatusEffect(), this.getTicksLeft(), this.getAmplifier(), this.isAmbient());
    }

    private static StatusEffect convert(MobEffect effect) {
        StatusEffect statusEffect;
        if (ObjectMappings.EFFECTS.inverse().containsKey(effect))
            statusEffect = ObjectMappings.EFFECTS.inverse().get(effect);
        else {
            ResourceLocation resourceLocation = Registry.MOB_EFFECT.getKey(effect);
            if (resourceLocation != null) {
                StatusEffect.Type type;
                MobEffectExtension mex = ((MobEffectExtension) effect);
                type = switch (mex.getCategory()) {
                    case BENEFICIAL -> StatusEffect.Type.BENEFICIAL;
                    case HARMFUL -> StatusEffect.Type.HARMFUL;
                    default -> StatusEffect.Type.NEUTRAL;
                };
                statusEffect = new StatusEffect.Other() {
                    final Type thisType = type;
                    final String id = effect.getDescriptionId();
                    @Override
                    public Type getType() {
                        return thisType;
                    }

                    @Override
                    public String getId() {
                        return id;
                    }
                };
            } else {
                throw new WrappingException("MobEffect " + effect.toString() + " not registered!");
            }
        }
        return statusEffect;
    }

    public static MobEffect convert(StatusEffect statusEffect) {
        if (ObjectMappings.EFFECTS.containsKey(statusEffect))
            return ObjectMappings.EFFECTS.get(statusEffect);
        else
            throw new WrappingException("StatusEffect has no pendant in vanilla!");
    }

    @Override
    public MobEffectInstance unwrap() {
        return this.wrapped;
    }
}
