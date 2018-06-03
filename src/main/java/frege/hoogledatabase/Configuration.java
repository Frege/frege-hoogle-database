package frege.hoogledatabase;

import com.beust.jcommander.Parameter;

import java.util.List;

import static java.util.Arrays.asList;

class Configuration {
    private static final String DEFAULT_ROOT_PACKGE = "frege";
    private static final String DEFAULT_BASE_URL = "http://www.frege-lang.org/doc";

    private static final List<String> DEFAULT_EXCLUSIONS = asList(
            // Exclude internal packages
            "^frege\\.(compiler|run\\d?|system|runtime|interpreter|scriptengine|repl||hoogledatabase)\\.",

            // Exclude Version module
            "^frege\\.Version$");

    private static final String DEFAULT_OUTPUT_FILE = "frege-hoogle-database.txt";

    @Parameter(names = {"-u", "--base-url"},
            description = "Frege documentation base URL", order = 1)
    private String docBaseUrlString = DEFAULT_BASE_URL;

    @Parameter(names = {"-p", "--root-package"}, description = "Root package", order = 2)
    private String rootPackage = DEFAULT_ROOT_PACKGE;

    @Parameter(names = {"-e", "--exclusions"}, description = "Package exclusions", order = 3)
    private List<String> exclusions = DEFAULT_EXCLUSIONS;

    @Parameter(names = {"-o", "--output"}, description = "Output file", order = 4)
    private String outputFile = DEFAULT_OUTPUT_FILE;

    @Parameter(names = "--help", help = true)
    private boolean help;

    public List<String> getExclusions() {
        return exclusions;
    }

    public String getDocBaseUrlString() {
        return docBaseUrlString;
    }

    public String getRootPackage() {
        return rootPackage;
    }

    public boolean isHelp() {
        return help;
    }

    public String getOutputFile() {
        return outputFile;
    }
}