package pt.lisboapedala.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Verificações sobre o texto do código-fonte e dos schema.sql (o ArchUnit só vê bytecode,
 * por isso não apanha imports curinga nem SQL). Sem dependências: só JDK.
 * São heurísticas por regex (ver docs/04-fitness-functions.md, secção "Limites").
 */
public final class SourceScanner {

    private SourceScanner() {}

    public record Finding(Path file, int line, String message) {
        @Override public String toString() { return file + ":" + line + "  " + message; }
    }

    // ------------------------------------------------------------------ FF-20
    private static final Pattern WILDCARD_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?[\\w.]+\\.\\*\\s*;");

    public static List<Finding> wildcardImports(Path javaRoot) {
        List<Finding> out = new ArrayList<>();
        for (Path f : files(javaRoot, ".java")) {
            String text = blank(read(f), true);
            Matcher m = WILDCARD_IMPORT.matcher(text);
            while (m.find()) {
                out.add(new Finding(f, lineOf(text, m.start()), "import curinga: " + m.group().strip()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ FF-21
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?[\"`\\[]?([A-Za-z_]\\w*)");

    /** tabela (minúsculas) -> módulo dono, deduzido de db/&lt;modulo&gt;/schema.sql. */
    public static Map<String, String> tableOwners(Path dbRoot) {
        Map<String, String> owners = new TreeMap<>();
        for (Path schema : files(dbRoot, "schema.sql")) {
            String module = schema.getParent().getFileName().toString();
            Matcher m = CREATE_TABLE.matcher(blank(read(schema), false));
            while (m.find()) owners.put(m.group(1).toLowerCase(), module);
        }
        return owners;
    }

    /** Código ou schema de um módulo que menciona (FROM/JOIN/INTO/UPDATE/REFERENCES) tabelas de outro. */
    public static List<Finding> foreignTableAccess(Path javaRoot, Path dbRoot) {
        Map<String, String> owners = tableOwners(dbRoot);
        List<Finding> out = new ArrayList<>();
        for (Path f : files(javaRoot, ".java")) {
            scanForeign(f, moduleOfJava(javaRoot, f), true, owners, out);
        }
        for (Path f : files(dbRoot, "schema.sql")) {
            scanForeign(f, f.getParent().getFileName().toString(), false, owners, out);
        }
        return out;
    }

    private static void scanForeign(Path file, String module, boolean java,
                                    Map<String, String> owners, List<Finding> out) {
        if (module == null) return;
        Set<String> foreign = owners.entrySet().stream()
                .filter(e -> !e.getValue().equals(module))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (foreign.isEmpty()) return;
        String alternation = foreign.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern p = Pattern.compile("(?i)\\b(?:from|join|into|update|references)\\s+"
                + "(?:if\\s+(?:not\\s+)?exists\\s+)?[\"`\\[]?(" + alternation + ")\\b");
        String text = blank(read(file), java);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String table = m.group(1).toLowerCase();
            out.add(new Finding(file, lineOf(text, m.start()),
                    "módulo '" + module + "' acede à tabela '" + table + "' do módulo '" + owners.get(table) + "'"));
        }
    }

    // ------------------------------------------------------------------ FF-22
    /** O schema declara UNIQUE/PRIMARY KEY (coluna, tabela ou índice) sobre uma coluna que case com o regex. */
    public static boolean hasUniqueOn(Path schemaFile, String columnRegex) {
        if (!Files.isRegularFile(schemaFile)) return false;
        String t = blank(read(schemaFile), false);
        String col = "\\w*(?:" + columnRegex + ")\\w*";
        Pattern columnLevel = Pattern.compile("(?is)\\b" + col + "\\b[^,;]*?\\b(?:primary\\s+key|unique)\\b");
        Pattern tableLevel = Pattern.compile("(?is)\\b(?:unique|primary\\s+key)\\s*\\([^)]*" + col + "[^)]*\\)");
        Pattern index = Pattern.compile("(?is)create\\s+unique\\s+index[^;]*\\([^)]*" + col + "[^)]*\\)");
        return columnLevel.matcher(t).find() || tableLevel.matcher(t).find() || index.matcher(t).find();
    }

    // ------------------------------------------------------------------ FF-23
    public static Set<String> tablesMatching(Path dbRoot, Pattern tableName) {
        return tableOwners(dbRoot).keySet().stream()
                .filter(n -> tableName.matcher(n).find())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** DELETE FROM / DROP TABLE sobre as tabelas indicadas, em qualquer ficheiro Java. */
    public static List<Finding> destructiveStatementsOn(Path javaRoot, Set<String> tables) {
        List<Finding> out = new ArrayList<>();
        if (tables.isEmpty()) return out;
        String alternation = tables.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern p = Pattern.compile("(?i)\\b(?:delete\\s+from|drop\\s+table(?:\\s+if\\s+exists)?)\\s+[\"`\\[]?("
                + alternation + ")\\b");
        for (Path f : files(javaRoot, ".java")) {
            String text = blank(read(f), true);
            Matcher m = p.matcher(text);
            while (m.find()) {
                out.add(new Finding(f, lineOf(text, m.start()), "operação destrutiva sobre '" + m.group(1) + "'"));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ utilitários
    static String moduleOfJava(Path javaRoot, Path file) {
        Path rel = javaRoot.relativize(file);
        for (int i = 0; i < rel.getNameCount() - 2; i++) {
            if (rel.getName(i).toString().equals("lisboapedala")) return rel.getName(i + 1).toString();
        }
        return null; // classe na raiz (LisboaPedalaApplication) ou fora do pacote
    }

    /** Substitui comentários por espaços (mantém quebras de linha, logo os números de linha). */
    static String blank(String s, boolean java) {
        StringBuilder o = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            char d = i + 1 < n ? s.charAt(i + 1) : '\0';
            if (java && s.startsWith("\"\"\"", i)) {                       // text block: mantém (pode ter SQL)
                int j = s.indexOf("\"\"\"", i + 3);
                j = j < 0 ? n : j + 3;
                o.append(s, i, j); i = j; continue;
            }
            if (java && (c == '"' || c == '\'')) {                         // string/char: mantém
                int j = i + 1;
                while (j < n && s.charAt(j) != c && s.charAt(j) != '\n') { if (s.charAt(j) == '\\') j++; j++; }
                j = Math.min(n, j + 1);
                o.append(s, i, j); i = j; continue;
            }
            boolean line = java ? (c == '/' && d == '/') : (c == '-' && d == '-');
            if (line) {
                while (i < n && s.charAt(i) != '\n') { o.append(s.charAt(i) == '\r' ? '\r' : ' '); i++; }
                continue;
            }
            if (c == '/' && d == '*') {
                int j = s.indexOf("*/", i + 2);
                j = j < 0 ? n : j + 2;
                for (int k = i; k < j; k++) { char x = s.charAt(k); o.append(x == '\n' || x == '\r' ? x : ' '); }
                i = j; continue;
            }
            o.append(c); i++;
        }
        return o.toString();
    }

    static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    private static List<Path> files(Path root, String suffix) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.naturalOrder()).toList();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static String read(Path f) {
        try { return Files.readString(f, StandardCharsets.UTF_8); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
