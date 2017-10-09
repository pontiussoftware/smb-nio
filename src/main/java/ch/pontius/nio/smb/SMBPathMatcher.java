package ch.pontius.nio.smb;

import java.nio.file.Path;
import java.nio.file.PathMatcher;

public class SMBPathMatcher implements PathMatcher {
    /** */
    private final String pattern;

    /**
     * Default constructor for {@link SMBPathMatcher}.
     *
     * @param pattern
     */
    SMBPathMatcher(String pattern) {
        if (pattern.startsWith("glob:")) {
            this.pattern = globToRegex(pattern.replaceFirst("glob:", ""));
        } else if (pattern.startsWith("regex:")) {
            this.pattern = pattern.replaceFirst("regex:", "");
        } else {
            this.pattern = pattern;
        }
    }

    /**
     * Converts a GLOB pattern to a RegEx pattern for internal use.
     *
     * @param globPattern The GLOB pattern that should be used.
     * @return RegEx pattern.
     */
    private static String globToRegex(String globPattern) {
        globPattern = globPattern.trim();
        int strLen = globPattern.length();
        StringBuilder sb = new StringBuilder(strLen);
        if (globPattern.startsWith("*")) {
            globPattern = globPattern.substring(1);
            strLen--;
        }
        if (globPattern.endsWith("*")) {
            globPattern = globPattern.substring(0, strLen-1);
            strLen--;
        }
        boolean escaping = false;
        int inCurlies = 0;
        for (char currentChar : globPattern.toCharArray()) {
            switch (currentChar)
            {
                case '*':
                    if (escaping)
                        sb.append("\\*");
                    else
                        sb.append(".*");
                    escaping = false;
                    break;
                case '?':
                    if (escaping)
                        sb.append("\\?");
                    else
                        sb.append('.');
                    escaping = false;
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                    sb.append('\\');
                    sb.append(currentChar);
                    escaping = false;
                    break;
                case '\\':
                    if (escaping)
                    {
                        sb.append("\\\\");
                        escaping = false;
                    }
                    else
                        escaping = true;
                    break;
                case '{':
                    if (escaping)
                    {
                        sb.append("\\{");
                    }
                    else
                    {
                        sb.append('(');
                        inCurlies++;
                    }
                    escaping = false;
                    break;
                case '}':
                    if (inCurlies > 0 && !escaping)
                    {
                        sb.append(')');
                        inCurlies--;
                    }
                    else if (escaping)
                        sb.append("\\}");
                    else
                        sb.append("}");
                    escaping = false;
                    break;
                case ',':
                    if (inCurlies > 0 && !escaping)
                    {
                        sb.append('|');
                    }
                    else if (escaping)
                        sb.append("\\,");
                    else
                        sb.append(",");
                    break;
                default:
                    escaping = false;
                    sb.append(currentChar);
            }
        }
        return sb.toString();
    }

    /**
     * Tells if given path matches this matcher's pattern
     *
     * @param path Path that should be checked.
     * @return True if path matches the pattern.
     */
    @Override
    public boolean matches(Path path) {
        return path.normalize().toString().matches(this.pattern);
    }
}
