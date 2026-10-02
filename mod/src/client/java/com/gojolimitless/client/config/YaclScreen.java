package com.gojolimitless.client.config;

import com.gojolimitless.config.Comment;
import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import com.gojolimitless.config.Range;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import com.gojolimitless.config.Group;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Builds the YACL config screen by reflection over {@link LimitlessConfig}: every section is a category and
 * every public field an option, so new settings appear automatically. Only loaded when YACL is installed.
 */
public final class YaclScreen {
    private YaclScreen() {}

    private static final java.util.Map<String, String> SECTION_NAMES = java.util.Map.of(
            "blue", "Lapse: Blue", "red", "Reversal: Red", "purple", "Hollow Purple",
            "nuke", "Remote Hollow Purple", "domain", "Unlimited Void");

    public static Screen create(Screen parent) {
        LimitlessConfig cfg = ConfigManager.get();
        LimitlessConfig defaults = new LimitlessConfig();
        YetAnotherConfigLib.Builder b = YetAnotherConfigLib.createBuilder()
                .title(Text.translatable("gojolimitless.config.title"))
                .save(ConfigManager::save);
        try {
            for (Field section : LimitlessConfig.class.getFields()) {
                if (Modifier.isStatic(section.getModifiers())) continue;
                Object inst = section.get(cfg), def = section.get(defaults);
                ConfigCategory.Builder cat = ConfigCategory.createBuilder().name(Text.literal(SECTION_NAMES.getOrDefault(section.getName(), pretty(section.getName()))));
                Comment sc = inst.getClass().getAnnotation(Comment.class);
                if (sc != null) cat.tooltip(Text.literal(sc.value()));
                OptionGroup.Builder group = null;
                for (Field f : inst.getClass().getFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    Group g = f.getAnnotation(Group.class);
                    if (g != null) {
                        if (group != null) cat.group(group.build());
                        group = OptionGroup.createBuilder().name(Text.literal(g.value()));
                    }
                    Option<?> o = option(f, inst, def);
                    if (o == null) continue;
                    if (group != null) group.option(o); else cat.option(o);
                }
                if (group != null) cat.group(group.build());
                b.category(cat.build());
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return b.build().generateScreen(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Option<?> option(Field f, Object inst, Object def) throws IllegalAccessException {
        Text name = Text.literal(pretty(f.getName()));
        Comment c = f.getAnnotation(Comment.class);
        OptionDescription desc = OptionDescription.of(Text.literal(c != null ? c.value() : ""));
        Range r = f.getAnnotation(Range.class);
        Class<?> t = f.getType();
        if (t == boolean.class) {
            return Option.<Boolean>createBuilder().name(name).description(desc)
                    .binding(f.getBoolean(def), () -> get(f, inst), v -> set(f, inst, v))
                    .controller(TickBoxControllerBuilder::create).build();
        }
        if (t == double.class && r != null) {
            double step = r.step() > 0 ? r.step() : (r.max() - r.min()) / 100.0;
            return Option.<Double>createBuilder().name(name).description(desc)
                    .binding(f.getDouble(def), () -> get(f, inst), v -> set(f, inst, v))
                    .controller(o -> DoubleSliderControllerBuilder.create(o).range(r.min(), r.max()).step(step)).build();
        }
        if (t == int.class && r != null) {
            int step = (int) Math.max(1, r.step());
            return Option.<Integer>createBuilder().name(name).description(desc)
                    .binding(f.getInt(def), () -> get(f, inst), v -> set(f, inst, v))
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range((int) r.min(), (int) r.max()).step(step)).build();
        }
        if (t.isEnum()) {
            Class<Enum> et = (Class<Enum>) t;
            return Option.<Enum>createBuilder().name(name).description(desc)
                    .binding((Enum) f.get(def), () -> get(f, inst), v -> set(f, inst, v))
                    .controller(o -> EnumControllerBuilder.create(o).enumClass(et)).build();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Field f, Object inst) {
        try { return (T) f.get(inst); } catch (IllegalAccessException e) { throw new RuntimeException(e); }
    }

    private static void set(Field f, Object inst, Object v) {
        try { f.set(inst, v); } catch (IllegalAccessException e) { throw new RuntimeException(e); }
    }

    /** camelCase → "Camel case" */
    static String pretty(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (i == 0) sb.append(Character.toUpperCase(ch));
            else if (Character.isUpperCase(ch)) sb.append(' ').append(Character.toLowerCase(ch));
            else sb.append(ch);
        }
        return sb.toString();
    }
}
