package dev.rex.farmbuilder.modules;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_9290;
import net.minecraft.class_9334;

final class AuctionUi {
    enum PriceFormat { Auto, English, German }
    enum Action { NEXT, REFRESH, CONFIRM }
    record Control(int slot, String text, boolean playerSlot, boolean pricedListing) {}
    private static final Map<Action, List<String>> ALIASES = Map.of(
        Action.NEXT, List.of("next", "next page", "weiter", "nachste", "naechste", "suivant", "siguiente", "proximo"),
        Action.REFRESH, List.of("refresh", "reload", "aktualisieren", "auffrischen", "neu laden", "actualiser", "refrescar"),
        Action.CONFIRM, List.of("confirm", "confirm purchase", "buy now", "purchase", "accept", "bestatigen", "bestaetigen", "kaufen", "acheter"));

    static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text.replaceAll("[§&][0-9a-fk-orxA-FK-ORX]", ""), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('\u00a0', ' ').trim();
    }

    static boolean words(String text, String phrase) {
        String key = normalize(phrase);
        if (key.isBlank()) return false;
        return java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(key)
            + "(?![\\p{L}\\p{N}])").matcher(normalize(text)).find();
    }

    static boolean blocked(String text, Action action) {
        String value = normalize(text);
        if (List.of("disabled", "unavailable", "not available", "deaktiviert", "nicht verfugbar").stream().anyMatch(value::contains)) return true;
        if (action == Action.NEXT && List.of("no next", "last page", "keine weitere", "letzte seite", "no more pages").stream().anyMatch(value::contains)) return true;
        String name = value.lines().findFirst().orElse("");
        return action == Action.CONFIRM && (List.of("cancel", "decline", "deny", "abbrechen", "ablehnen").stream().anyMatch(word -> words(name, word))
            || List.of("do not buy", "nicht kaufen", "don't confirm", "do not confirm").stream().anyMatch(value::contains));
    }

    static int choose(List<Control> controls, Action action, String custom, int override) {
        if (override >= 0) return controls.stream().filter(c -> c.slot() == override && !c.playerSlot()
            && !blocked(c.text(), action) && (action == Action.CONFIRM || !c.pricedListing())).mapToInt(Control::slot).findFirst().orElse(-1);
        int best = -1, bestScore = 0;
        for (Control c : controls) {
            if (c.playerSlot() || blocked(c.text(), action) || action != Action.CONFIRM && c.pricedListing()) continue;
            int score = words(c.text(), custom) ? 100 : 0;
            for (String alias : ALIASES.get(action)) if (words(c.text(), alias)) score = Math.max(score, 20 + alias.length());
            String name = c.text().lines().findFirst().orElse("");
            if (action == Action.CONFIRM && c.pricedListing() && !words(name, "confirm") && !words(name, "bestatigen") && !words(name, "bestaetigen") && !words(name, custom)) continue;
            if (score > bestScore) { bestScore = score; best = c.slot(); }
        }
        return best;
    }

    static double price(String text, PriceFormat format) {
        if (text == null || text.matches(".*-\\s*[$€]?\\s*[0-9].*")) return -1;
        String input = text.replaceAll("(?<=\\d)[\\u00a0\\u202f ](?=\\d{3}(?:\\D|$))", "");
        java.util.regex.Matcher token = java.util.regex.Pattern.compile("[0-9][0-9.,]*").matcher(input);
        boolean german = format == PriceFormat.German;
        if (format == PriceFormat.Auto && token.find()) {
            String number = token.group();
            int comma = number.lastIndexOf(',');
            german = comma >= 0 && (number.contains(".") && comma > number.lastIndexOf('.')
                || !number.contains(".") && number.length() - comma - 1 <= 2);
        }
        if (!german) return FarmLogic.parsePrice(input);
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("(?<![0-9.,-])(?:[0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]+)(?:,[0-9]{1,2})?(?![0-9.,])").matcher(input);
        if (!number.find()) return -1;
        String converted = number.group().replace(".", "").replace(',', '.');
        return FarmLogic.parsePrice(input.substring(0, number.start()) + converted + input.substring(number.end()));
    }

    static boolean confirmContext(boolean strongTitle, boolean hasConfirm, boolean hasCancel, boolean selectedPresent, boolean changedHandler, boolean configured) {
        return hasConfirm && (strongTitle || hasCancel && selectedPresent || changedHandler && selectedPresent
            || configured && hasCancel && changedHandler);
    }

    static String text(class_1799 stack) {
        if (stack.method_7960()) return "";
        StringBuilder text = new StringBuilder(stack.method_7964().getString());
        class_9290 lore = stack.method_58694(class_9334.field_49632);
        if (lore != null) for (class_2561 line : lore.comp_2400()) text.append('\n').append(line.getString());
        return text.toString();
    }

    static String stableText(String text) {
        return normalize(text).lines().filter(line -> !List.of("expires", "expiration", "time left", "remaining", "ablauf", "verbleibend").stream().anyMatch(line::contains))
            .collect(java.util.stream.Collectors.joining("\n"));
    }
}
