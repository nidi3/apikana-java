package org.swisspush.apikana;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;

/**
 * Generate JSON schemas and a user documentation in HTML from the given swagger and typescript models.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateMojo extends AbstractApikanaMojo {
    private static final Logger LOG = LoggerFactory.getLogger(GenerateMojo.class);

    /**
     * {@code --basePath} parameter for apikana.
     */
    @Parameter( property="apikana.base-path" )
    private String basePath;

    /**
     * {@code --generate1stGenPaths} parameter for apikana.
     */
    @Parameter( property="apikana.generate-1st-gen-paths")
    private String generate1stGenPaths;

    /**
     * {@code --generate2ndGenPaths} parameter for apikana.
     */
    @Parameter( property="apikana.generate-2nd-gen-paths")
    private String generate2ndGenPaths;

    /**
     * {@code --generate3rdGenPaths} parameter for apikana.
     */
    @Parameter( property="apikana.generate-3rd-gen-paths")
    private String generate3rdGenPaths;

    /**
     * The node version to be used.
     */
    @Parameter(defaultValue = "v10.23.0", property = "apikana.node-version")
    private String nodeVersion;

    /**
     * The npm version to be used.
     */
    @Parameter(defaultValue = "6.14.8", property = "apikana.npm-version")
    private String npmVersion;

    /**
     * The url to download npm and node from.
     */
    @Parameter(property = "apikana.download-root")
    private String downloadRoot;

    /**
     * Options to run npm with. This is used to install apikana and to run apikana.
     */
    @Parameter(defaultValue = "", property = "apikana.npm-options")
    private String npmOptions;

    /**
     * The apikana npm version to be used.
     */
    @Parameter(defaultValue = "0.4.13", property = "apikana.version")
    private String apikanaVersion;

    /**
     * The main API file (yaml or json).
     */
    @Parameter(defaultValue = "src/openapi/api.yaml", property = "apikana.api")
    private String api;

    /**
     * The directory containing the models, if no API file is given.
     */
    @Parameter(defaultValue = "src/ts", property = "apikana.models")
    private String models;

    /**
     * The java package that should be used.
     */
    @Parameter(property = "apikana.java-package")
    private String javaPackage;

    /**
     * The path prefix to be used in the generated *Paths.java file.
     * If the property is not set or "null", the maximum possible path prefix is used.
     */
    @Parameter(property = "apikana.path-prefix")
    private String pathPrefix;

    /**
     * If the sources should be copied into the output directory.
     */
    @Parameter(defaultValue = "false", property = "apikana.deploy")
    private boolean deploy;

    /**
     * The port of the HTTP server.
     */
    @Parameter(defaultValue = "8333", property = "apikana.port")
    private int port;

    /**
     * If the API should be published via HTTP.
     */
    @Parameter(defaultValue = "true", property = "apikana.serve")
    private boolean serve;

    /**
     * If the browser should be opened to show the API.
     */
    @Parameter(defaultValue = "true", property = "apikana.open-browser")
    private boolean openBrowser;

    /**
     * If the globally installed apikana node package should be used.
     */
    @Parameter(defaultValue = "false", property = "apikana.global")
    private boolean global;

    public void execute() throws MojoExecutionException {
        try {
            if (isPom()) {
                getLog().info("Packaging is pom. Skipping generation.");
                mavenProject.getProperties().setProperty("jsonschema2pojo.skip", "true");
            } else {
                unpackStyleDependencies(mavenProject.getParent());
                unpackModelDependencies();
                writeProjectProps();
                if (global) {
                    checkNodeInstalled();
                } else {
                    installNode();
                    generatePackageJson(apikanaVersion);
                    installApikana();
                }
                deleteGeneratedClasses();
                runApikana();
                mavenProject.addCompileSourceRoot(file(OUTPUT + "/model/java").getAbsolutePath());
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Problem running apikana", e);
        }
    }

    private void deleteGeneratedClasses() throws IOException {
        //for some reason, jsonschema2pojo does not generate .java when .class already exists
        final File outDir = new File(mavenProject.getBuild().getOutputDirectory() + "/" + javaPackage().replace('.', '/'));
        if (outDir.exists()) {
            FileUtils.cleanDirectory(outDir);
        }
    }

    private void installNode() throws MojoExecutionException {
        executeFrontend("install-node-and-npm", configuration(
                element("downloadRoot", downloadRoot),
                element("nodeVersion", nodeVersion),
                element("npmVersion", npmVersion)
        ));
    }

    private void installApikana() throws IOException, MojoExecutionException {
        final File apikanaPackage = working("node_modules/apikana/package.json");
        if (apikanaPackage.exists()) {
            Map pack = new ObjectMapper().readValue(apikanaPackage, Map.class);
            final String version = (String) pack.get("version");
            if (apikanaVersion.equals(version)) {
                getLog().info("apikana " + apikanaVersion + " already installed.");
                return;
            }
        }
        executeFrontend("npm", configuration(element("arguments", npmOptions() + "install")));
    }

    private void runApikana() throws Exception {
        final List<String> cmd = new ArrayList<>(asList("apikana start",
                relative(working(""), file("")),
                global ? "" : "--",
                "--api=" + api,
                models != null && models.trim().length() > 0 ? "--models=" + models : "",
                "--target=" + relative(working(""), file(OUTPUT)),
                "--style=" + style,
                "--javaPackage=" + javaPackage(),
                "--deploy=" + deploy,
                "--port=" + port,
                "--serve=" + serve,
                "--openBrowser=" + openBrowser,
                "--config=properties.json",
                "--dependencyPath=" + relative(working(""), apiDependencies("")),
                "--minVersion=" + apikanaVersion,
                "--log=" + logLevel()));
        if (pathPrefix != null && !"null".equals(pathPrefix)) {
            cmd.add("--pathPrefix=" + pathPrefix);
        }
        if( basePath != null && !"null".equals(basePath) ){
            cmd.add( "--basePath="+ basePath );
        }
        if( boolArgIsSet(generate1stGenPaths) ){
            cmd.add( "--generate1stGenPaths="+ encodeArgAsNonNullBoolean(generate1stGenPaths) );
        }
        if( boolArgIsSet(generate2ndGenPaths) ){
            cmd.add( "--generate2ndGenPaths="+ encodeArgAsNonNullBoolean(generate2ndGenPaths) );
        }
        if( boolArgIsSet(generate3rdGenPaths) ){
            cmd.add( "--generate3rdGenPaths="+ encodeArgAsNonNullBoolean(generate3rdGenPaths) );
        }
        final String cmdLine = cmd.stream().collect(Collectors.joining(" "));
        if (global) {
            final Process apikana = shellCommand(working(""), cmdLine).inheritIO().start();
            if (apikana.waitFor() != 0) {
                throw new IOException();
            }
        } else {
            executeFrontend("npm", configuration(element("arguments", npmOptions() + "run " + cmdLine)));
        }
    }

    private boolean boolArgIsSet( String argValue ) {
        return argValue != null && !argValue.isEmpty();
    }

    private String encodeArgAsNonNullBoolean( String argValue ) {
        return ( argValue != null && !argValue.isEmpty() && !"FALSE".equalsIgnoreCase(argValue) )
                ? "true"
                : "false"
        ;
    }

    private String logLevel() {
        if (LOG.isDebugEnabled() || LOG.isTraceEnabled()) {
            return "debug";
        }
        if (LOG.isInfoEnabled()) {
            return "info";
        }
        if (LOG.isWarnEnabled()) {
            return "warn";
        }
        return "error";
    }

    private String npmOptions() throws MojoExecutionException {
        if (npmOptions == null || npmOptions.trim().length() == 0) {
            return "";
        }
        if (!npmOptions.startsWith("--")) {
            throw new MojoExecutionException("npmOptions must start with --");
        }
        return npmOptions.trim() + " ";
    }

    private String javaPackage() {
        String artifactId = mavenProject.getArtifactId();
        int point = artifactId.indexOf('-');
        if (point > 0) {
            artifactId = artifactId.substring(point + 1);
        }
        artifactId = artifactId.replace("-", ".");
        return javaPackage != null ? javaPackage : (mavenProject.getGroupId() + "." + artifactId);
    }

}
