// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.colorScheme;

import consulo.logging.Logger;
import consulo.ui.color.ColorValue;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

import static consulo.colorScheme.EffectType.*;
import static consulo.colorScheme.TextAttributesEffectsBuilder.EffectSlot.*;

/**
 * Allows to build effects for the TextAttributes. Allows to cover effects on the current state and slip effects under it.
 */
public class TextAttributesEffectsBuilder {
    private static final Logger LOG = Logger.getInstance(TextAttributesEffectsBuilder.class);

    public enum EffectSlot {
        FRAME_SLOT,
        UNDERLINE_SLOT,
        STRIKE_SLOT
    }

    // this probably could be a property of the EffectType
    private static final Map<EffectType, EffectSlot> EFFECT_SLOTS_MAP = Map.of(
        STRIKEOUT, STRIKE_SLOT,
        BOXED, FRAME_SLOT,
        ROUNDED_BOX, FRAME_SLOT,
        BOLD_LINE_UNDERSCORE, UNDERLINE_SLOT,
        LINE_UNDERSCORE, UNDERLINE_SLOT,
        WAVE_UNDERSCORE, UNDERLINE_SLOT,
        BOLD_DOTTED_LINE, UNDERLINE_SLOT
    );

    private final Map<EffectSlot, EffectDescriptor> myEffectsMap = new HashMap<>(EffectSlot.values().length);

    private TextAttributesEffectsBuilder() {
    }

    /**
     * Creates a builder without any effects
     */
    @Nonnull
    public static TextAttributesEffectsBuilder create() {
        return new TextAttributesEffectsBuilder();
    }

    /**
     * Creates a builder with effects from {@code deepestAttributes}
     */
    @Nonnull
    public static TextAttributesEffectsBuilder create(@Nonnull TextAttributes deepestAttributes) {
        return create().coverWith(deepestAttributes);
    }

    /**
     * Applies effects from {@code attributes} above current state of the merger. Effects may override mutually exclusive ones. E.g
     * If current state has underline and we applying attributes with wave underline, underline effect will be removed.
     */
    @Nonnull
    public final TextAttributesEffectsBuilder coverWith(@Nonnull TextAttributes attributes) {
        attributes.forEachAdditionalEffect(this::coverWith);
        coverWith(attributes.getEffectType(), attributes.getEffectColor());
        return this;
    }

    /**
     * Applies effects from {@code attributes} if effect slots are not used.
     */
    @Nonnull
    public final TextAttributesEffectsBuilder slipUnder(@Nonnull TextAttributes attributes) {
        slipUnder(attributes.getEffectType(), attributes.getEffectColor());
        attributes.forEachAdditionalEffect(this::slipUnder);
        return this;
    }

    /**
     * Applies effect with {@code effectType} and {@code effectColor} to the current state. Effects may override mutually exclusive ones. E.g
     * If current state has underline and we applying attributes with wave underline, underline effect will be removed.
     */
    @Nonnull
    public TextAttributesEffectsBuilder coverWith(@Nullable EffectType effectType, @Nullable ColorValue effectColor) {
        return mutateBuilder(effectType, effectColor, myEffectsMap::put);
    }

    /**
     * Applies effect with {@code effectType} and {@code effectColor} to the current state if effect slot is not used.
     */
    @Nonnull
    public TextAttributesEffectsBuilder slipUnder(@Nullable EffectType effectType, @Nullable ColorValue effectColor) {
        return mutateBuilder(effectType, effectColor, myEffectsMap::putIfAbsent);
    }

    @Nonnull
    private TextAttributesEffectsBuilder mutateBuilder(
        @Nullable EffectType effectType,
        @Nullable ColorValue effectColor,
        @Nonnull BiConsumer<? super EffectSlot, ? super EffectDescriptor> slotMutator
    ) {
        if (effectColor != null && effectType != null) {
            EffectSlot slot = EFFECT_SLOTS_MAP.get(effectType);
            if (slot != null) {
                slotMutator.accept(slot, EffectDescriptor.create(effectType, effectColor));
            }
            else {
                LOG.debug("Effect " + effectType + " is not supported by builder");
            }
        }
        return this;
    }

    /**
     * @return map of {@link EffectType} => {@link Color} representation of builder state
     */
    @Nonnull
    Map<EffectType, ColorValue> getEffectsMap() {
        if (myEffectsMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<EffectType, ColorValue> result = new HashMap<>();
        myEffectsMap.forEach((key, val) -> {
            if (val != null) {
                result.put(val.effectType, val.effectColor);
            }
        });
        return result;
    }

    /**
     * Applies effects from the current state to the target attributes
     *
     * @param targetAttributes passed targetAttributes
     * @apiNote this method is not a thread safe, builder can't be modified in some other thread when applying to something
     */
    @Nonnull
    public TextAttributes applyTo(@Nonnull final TextAttributes targetAttributes) {
        Iterator<EffectDescriptor> effectsIterator = myEffectsMap.values().iterator();
        if (!effectsIterator.hasNext()) {
            targetAttributes.setEffectColor(null);
            targetAttributes.setEffectType(BOXED);
            targetAttributes.setAdditionalEffects(Collections.emptyMap());
        }
        else {
            EffectDescriptor mainEffectDescriptor = effectsIterator.next();
            targetAttributes.setEffectType(mainEffectDescriptor.effectType);
            targetAttributes.setEffectColor(mainEffectDescriptor.effectColor);

            int effectsLeft = myEffectsMap.size() - 1;
            if (effectsLeft == 0) {
                targetAttributes.setAdditionalEffects(Collections.emptyMap());
            }
            else if (effectsLeft == 1) {
                EffectDescriptor additionalEffect = effectsIterator.next();
                targetAttributes.setAdditionalEffects(Collections.singletonMap(additionalEffect.effectType, additionalEffect.effectColor));
            }
            else {
                Map<EffectType, ColorValue> effectsMap = new HashMap<>(effectsLeft);
                effectsIterator.forEachRemaining(it -> effectsMap.put(it.effectType, it.effectColor));
                targetAttributes.setAdditionalEffects(effectsMap);
            }
        }
        return targetAttributes;
    }

    @Nullable
    @Contract("null -> null")
    public EffectDescriptor getEffectDescriptor(@Nullable EffectSlot effectSlot) {
        return myEffectsMap.get(effectSlot);
    }

    public static class EffectDescriptor {
        @Nonnull
        public final EffectType effectType;
        @Nonnull
        public final ColorValue effectColor;

        private EffectDescriptor(@Nonnull EffectType effectType, @Nonnull ColorValue effectColor) {
            this.effectType = effectType;
            this.effectColor = effectColor;
        }

        @Nonnull
        static EffectDescriptor create(@Nonnull EffectType effectType, @Nonnull ColorValue effectColor) {
            return new EffectDescriptor(effectType, effectColor);
        }
    }
}
