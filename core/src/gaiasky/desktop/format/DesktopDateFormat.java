/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.desktop.format;

import gaiasky.util.format.IDateFormat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;

public class DesktopDateFormat implements IDateFormat {
    private DateTimeFormatter df;

    public DesktopDateFormat(final String pattern) {
        df = DateTimeFormatter.ofPattern(pattern).withLocale(Locale.US).withZone(ZoneOffset.UTC);
    }

    public DesktopDateFormat(Locale loc, final boolean date, final boolean time) {
        assert date || time : "Formatter must include date or time";
        if(loc == null)
            loc = Locale.US;
        if (date && !time) {
            df = DateTimeFormatter.ofPattern("MMM dd uuuu").withLocale(loc).withZone(ZoneOffset.UTC);
        } else if (!date)
            df = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(loc).withZone(ZoneOffset.UTC);
        else
            df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM).withLocale(loc).withZone(ZoneOffset.UTC);
    }

    @Override
    public String format(Instant date) {
        return df.format(date);
    }

    @Override
    public Instant parse(String date) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(date, df);
            return ldt.toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new RuntimeException(e);
        }
    }

}
