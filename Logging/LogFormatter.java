package Logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
    /**
     * Formats a log record.
     *
     * @param record The log record to format.
     * @return The formatted log record.
     */
    
    @Override
    public String format(LogRecord record) {
        final Date date = new Date(record.getMillis());
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final String formattedDate = dateFormat.format(date);

        final StringBuilder builder = new StringBuilder();
        builder.append(formattedDate);
        builder.append(": ");
        builder.append(record.getMessage());
        builder.append("\n");

        return builder.toString();
    }

}
