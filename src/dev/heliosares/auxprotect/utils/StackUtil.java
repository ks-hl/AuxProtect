package dev.heliosares.auxprotect.utils;

import java.util.Arrays;
import java.util.Map.Entry;

public class StackUtil {
    public static String dumpThreadStack() {
        String trace = "";
        for (Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            if (entry.getValue().length == 0 || !entry.getKey().isAlive()) {
                continue;
            }
            if (trace.length() > 0) {
                trace += "\n\n";
            }
            trace += "Thread #" + entry.getKey().getId() + ":";
            trace += format(entry.getValue(), 20);
        }
        return trace;
    }

    public static String format(Throwable t, int linelimit) {
        String out = String.format("%s: %s", t.getClass().getName(), t.getMessage());

        out += format(t.getStackTrace(), linelimit);
        return out;
    }

    public static String format(StackTraceElement[] stack, int linelimit) {
        StringBuilder out = new StringBuilder();
        Arrays.asList(stack).stream().limit(linelimit).forEach((s) -> {
            out.append(String.format("\n    at %s.%s(%s:%s)", s.getClassName(), s.getMethodName(), s.getFileName(),
                    s.getLineNumber()));
        });
        if (linelimit < stack.length) {
            out.append("\n    [" + (stack.length - linelimit) + " more lines]");
        }
        return out.toString();
    }
}
