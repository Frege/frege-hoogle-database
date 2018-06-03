package frege.hoogledatabase;

import com.beust.jcommander.JCommander;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.partition;
import static com.google.common.html.HtmlEscapers.htmlEscaper;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.nio.file.Files.newBufferedWriter;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static Pattern anyCharPattern = Pattern.compile("\\p{L}");

    private final String baseUrl;
    private final String rootPackageName;
    private final Path outputPath;
    private BufferedWriter out;
    private final List<Pattern> exclusionPatterns;

    private Main(Configuration config) {
        baseUrl = config.getDocBaseUrlString();
        rootPackageName = config.getRootPackage();
        exclusionPatterns = config.getExclusions().stream()
                .map(Pattern::compile)
                .collect(toList());
        outputPath = Paths.get(config.getOutputFile());
    }

    public static void main(String[] args) {
        Configuration config = parseArgs(args);
        new Main(config).generateHoogleDb();
    }

    private static Configuration parseArgs(String[] args) {
        Configuration config = new Configuration();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(config)
                .programName(Main.class.getCanonicalName())
                .build();
        jCommander.parse(args);
        if (config.isHelp()) {
            jCommander.usage();
            System.exit(0);
        }
        return config;
    }

    private void generateHoogleDb() {
        try {
            try (BufferedWriter out = newBufferedWriter(outputPath, Charset.forName("UTF-8"))) {
                this.out = out;
                List<Class<?>> classes = packageClasses(rootPackageName);
                classes.stream()
                        .filter(this::shouldInclude)
                        .forEach(clazz -> generateHoogleDb(clazz.getName()));
                out.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean shouldInclude(Class<?> clazz) {
        return exclusionPatterns.stream()
                .noneMatch(exclPattern -> exclPattern.matcher(clazz.getName()).find());
    }

    private void generateHoogleDb(String moduleName) {
        String modulePath = Arrays.stream(moduleName.split("\\.")).collect(joining("/"));
        try {
            Document document = getDocument(modulePath);
            generateModule(moduleName, modulePath, document);

            Elements xs = document.select("dl.data");
            if (!xs.isEmpty()) {
                Elements moduleMembers = xs.first().children();
                generate(moduleMembers, modulePath);
            }

        } catch (Exception e) {
            LOGGER.warn("Unable to generate for module {}: {}", () -> moduleName, e::toString);
        }
    }

    private void generateModule(String moduleName, String modulePath, Document document) {
        Element current = document.select("h1")
                .first()
                .nextElementSibling();

        StringBuilder moduleDocBuilder = new StringBuilder();
        while (current != null && !isImports(current)) {
            moduleDocBuilder.append(getText(current, baseUrl + modulePath));
            current = current.nextElementSibling();
        }
        String moduleDoc = prefixLineCommentChars(replaceNewlinePlaceHolder(moduleDocBuilder.toString()));
        if (!moduleDoc.isEmpty()) {
            writeLn(moduleDoc);
        }
        writeLn("module " + moduleName);
        writeLn();
    }

    private boolean isImports(Element element) {
        return element.tagName().equals("h3") && element.text().trim().equals("Imports");
    }

    private String prefixLineCommentChars(String text) {
        String trimmed = text.trim();
        if (!trimmed.isEmpty()) {
            return "-- | " +
                    Arrays.stream(trimmed.split("\\n"))
                            .collect(joining("\n--  "));
        } else {
            return trimmed;
        }
    }

    private String getText(Element element, String moduleUrl) {
        class Local {
            private String go(Node node) {
                String tagName = getTagName(node);
                if (node instanceof TextNode) {
                    return ((TextNode) node).text();
                } else if (tagName.matches("p|dt|dd")) {
                    String text = processChildren(node);
                    return "\n" + text;
                } else if (tagName.matches("dl")) {
                    return processChildren(node);
                } else if (tagName.equals("pre")) {
                    return "\n<pre>\n" +
                            htmlEscaper().escape(((TextNode) node.childNodes().get(0)).getWholeText()) +
                            "\n</pre>";
                } else if (!tagName.isEmpty()) {
                    String childText = processChildren(node);
                    StringBuilder attrBuilder = new StringBuilder();
                    Attributes attrs = node.attributes();
                    for (Attribute attr : attrs) {
                        String key = attr.getKey();
                        String value = attr.getValue();
                        if (key.equals("href")) {
                            if (value.startsWith("#"))
                                value = moduleUrl + value;
                            else if (value.startsWith(".")) {
                                String baseUrl = moduleUrl.substring(0, moduleUrl.lastIndexOf("/"));
                                value = baseUrl + "/" + value;
                            }
                        }
                        attrBuilder
                                .append(key)
                                .append("=\"")
                                .append(value)
                                .append("\" ");
                    }
                    return format("<%1$s %2$s>%3$s</%1$s>", tagName, attrBuilder.toString(), childText);
                } else {
                    return processChildren(node);
                }
            }

            private String getTagName(Node node) {
                if (node instanceof Element)
                    return ((Element) node).tagName();
                else
                    return "";
            }

            private String processChildren(Node node) {
                StringBuilder text = new StringBuilder();
                node.childNodes().forEach(c -> text.append(go(c)));
                return text.toString();
            }

        }
        return new Local().go(element);
    }

    private static List<Class<?>> packageClasses(String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            ImmutableSet<ClassPath.ClassInfo> clsInfos =
                    com.google.common.reflect.ClassPath.from(currentThread().getContextClassLoader())
                            .getTopLevelClassesRecursive(packageName);
            for (ClassPath.ClassInfo clsInfo : clsInfos) {
                Class<?> cls = clsInfo.load();
                if (!cls.isSynthetic() && !cls.isAnonymousClass() && !cls.isLocalClass())
                    classes.add(cls);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return classes;
    }

    private Document getDocument(String modulePath) throws IOException {
        Document document = Jsoup.connect(baseUrl + "/" + modulePath + ".html").get();

        //makes html() preserve linebreaks and spacing
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));

        document.select("br").after("$newline$");
        return document;
    }

    private void generate(Elements members, String modulePath) {
        partition(members, 2).forEach(member -> {
            docItem(member, modulePath);
            if (member.size() > 1) {
                Elements functions = member.get(1).select("dl.func");
                if (!functions.isEmpty()) {
                    Elements memberFunctions = functions.first().children();
                    partition(memberFunctions, 2)
                            .forEach(m -> docItem(m, modulePath));
                }

            }
        });
    }

    private void docItem(List<Element> member, String modulePath) {

        if (member.size() > 1) {
            Element docNode = member.get(1);
            String docText = prefixLineCommentChars(
                    replaceNewlinePlaceHolder(getText(docNode, baseUrl + modulePath)));
            if (!docText.isEmpty())
                writeReplacingNewLinePlaceholder(docText + "\n");
        }

        Element typeNode = member.get(0);
        writeReplacingNewLinePlaceholder("@url " + baseUrl + modulePath + '#' + typeNode.select("a[name]").attr("name") + '\n');

        typeNode.children().forEach(x -> {
            String text = x.text();
            String[] decl = text.split("∷");
            if (decl.length > 1 && !anyCharPattern.matcher(decl[0].trim()).find()) { // operator function
                writeReplacingNewLinePlaceholder('(' + decl[0].trim() + ") ∷ " + decl[1] + '\n');
            } else {
                writeReplacingNewLinePlaceholder(postProcess(text) + '\n');
            }

        });
        writeLn();
    }

    private void writeReplacingNewLinePlaceholder(String s) {
        write(replaceNewlinePlaceHolder(s));
    }

    private void writeLn(String str) {
        write(str);
        writeLn();
    }

    private void write(String str) {
        unchecked(() -> out.write(str));
    }

    private void writeLn() {
        unchecked(() -> out.newLine());
    }

    private static void unchecked(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private String postProcess(String text) {
        if (text.trim().matches("data.*=.*\\snative\\s.*")) {
            return text.split("=")[0];
        } else {
            return text;
        }
    }

    private String replaceNewlinePlaceHolder(String s) {
        return s.replaceAll("\\$newline\\$", "\n");
    }

}
