package org.swisspush.apikana;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;

/**
 * Generate JSON schemas and a user documentation in HTML from the given swagger and typescript models.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateMojo extends AbstractGenerateMojo {
    private final static String OUTPUT = "target/api";

    private static class Version {
        static final String APIKANA = "0.2.3";
    }

    /**
     * The working directory for node.
     */
    @Parameter(defaultValue = "target/node", property = "apikana.node-working-dir")
    private File nodeWorkingDir;

    /**
     * The node version to be used.
     */
    @Parameter(defaultValue = "v7.5.0", property = "apikana.node-version")
    private String nodeVersion;

    /**
     * The npm version to be used.
     */
    @Parameter(defaultValue = "4.2.0", property = "apikana.npm-version")
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
     * The directory containing css files and images to style the swagger GUI.
     */
    @Parameter(defaultValue = "src/style", property = "apikana.style")
    private String style;

    /**
     * The java package that should be used.
     */
    @Parameter(property = "apikana.java-package")
    private String javaPackage;

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

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (!handlePomPackaging()) {
                unpackStyleDependencies(mavenProject.getParent());
                unpackModelDependencies();
                writeProjectProps();
                if (global) {
                    checkNodeInstalled();
                } else {
                    installNode();
                    generatePackageJson(Version.APIKANA);
                    installApikana();
                }
                deleteGeneratedClasses();
                runApikana();
                mavenProject.addCompileSourceRoot(file(OUTPUT + "/model/java").getAbsolutePath());
                addResource(mavenProject, file(OUTPUT).getAbsolutePath(), null, Arrays.asList(
                        "model/json-schema-v3/**", "model/json-schema-v4/**", "model/openapi/**", "model/ts/**", "ui/style/**"));

                projectHelper.attachArtifact(mavenProject, createApiJar(OUTPUT), "api");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Problem running apikana", e);
        }
    }

    protected boolean handlePomPackaging() throws IOException {
        if ("pom".equals(mavenProject.getPackaging())) {
            getLog().info("Packaging is pom. Skipping execution.");
            mavenProject.getProperties().setProperty("jsonschema2pojo.skip", "true");
            if (file(style).exists()) {
                projectHelper.attachArtifact(mavenProject, "jar", "style", createStyleJar());
            }
            return true;
        }
        return false;
    }

    private File createStyleJar() throws IOException {
        final File file = styleJarFile();
        file.getParentFile().mkdirs();
        try (JarOutputStream zs = new JarOutputStream(new FileOutputStream(file))) {
            IoUtils.addDirToZip(zs, file(style), "ui/style");
        }
        return file;
    }

    @Override
    protected File working(String name) {
        return new File(nodeWorkingDir, name);
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
            if (Version.APIKANA.equals(version)) {
                getLog().info("apikana " + Version.APIKANA + " already installed.");
                return;
            }
        }
        executeFrontend("npm", configuration(element("arguments", npmOptions + " install")));
    }

    private void runApikana() throws Exception {
        final List<String> cmd = Arrays.asList("apikana start",
                relative(working(""), file("")),
                relative(working(""), file(OUTPUT)),
                global ? "" : "--",
                "--api=" + api,
                models != null && models.trim().length() > 0 ? "--models=" + models : "",
                "--style=" + style,
                "--javaPackage=" + javaPackage(),
                "--deploy=" + deploy,
                "--port=" + port,
                "--serve=" + serve,
                "--openBrowser=" + openBrowser,
                "--config=properties.json",
                "--dependencyPath=" + relative(working(""), apiDependencies("")));
        final String cmdLine = cmd.stream().collect(Collectors.joining(" "));
        if (global) {
            final Process apikana = shellCommand(working(""), cmdLine).inheritIO().start();
            if (apikana.waitFor() != 0) {
                throw new IOException();
            }
        } else {
            executeFrontend("npm", configuration(element("arguments", npmOptions + " run " + cmdLine)));
        }
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
