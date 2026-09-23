package io.github.andis382.carelog.messaging;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Message texts live in messages*.properties under "msg.&lt;key&gt;" with named placeholders:
 * msg.supplies_low=Hi {name}, ... {link}. A line made only of placeholders that all came out
 * empty is dropped, so optional sections (no problems this week) leave no blank gaps.
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-zA-Z]+}");

    private final MessageSource messages;

    public TemplateRenderer(MessageSource messages) {
        this.messages = messages;
    }

    public String render(String key, String locale, Map<String, String> params) {
        Locale loc = Locale.forLanguageTag(locale == null ? "sq" : locale);
        // With no arguments Spring returns the raw pattern, so apostrophes need no escaping here.
        String template = messages.getMessage("msg." + key, null, key, loc);
        return template.lines()
            .map(line -> new String[] {line, fill(line, params)})
            .filter(pair -> !(PLACEHOLDER.matcher(pair[0]).find() && pair[1].isBlank()))
            .map(pair -> pair[1])
            .collect(Collectors.joining("\n"));
    }

    private static String fill(String line, Map<String, String> params) {
        String text = line;
        for (Map.Entry<String, String> e : params.entrySet()) {
            text = text.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return text;
    }
}
